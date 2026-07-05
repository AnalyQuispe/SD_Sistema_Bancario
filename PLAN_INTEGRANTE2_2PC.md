# Integrante 2 — Transferencias distribuidas con Two-Phase Commit (2PC)

> **Rúbrica que cubre:** *Transacciones y control de concurrencia* + *Transacciones distribuidas* (4 pts).
> **Depende de:** Integrante 1 (`BancoRemotoClient`, peers, endpoints `/internal/**`).
> **Estado:** ✅ **Esqueleto implementado y verificado end-to-end** (ver §6). Quedan refinamientos (§7).

---

## 1. Qué se pedía y cómo se resolvió

Una transferencia **entre bancos distintos** (p. ej. `A-1001 → C-3001`) se ejecuta con 2PC:
el banco que **recibe** la petición es el **coordinador**; los bancos dueños de la cuenta origen
y destino son **participantes**. O se aplica en todos, o en ninguno (atomicidad distribuida).

**Punto de enganche:** antes, `TransferenciaService.buscarLocal()` devolvía **HTTP 501** para
cuentas de otro banco. Ahí se enchufó el enrutado local/distribuido.

### Modelo de reserva: "débito tentativo"
En vez de un saldo-disponible separado, el **origen resta el importe ya en PREPARE** (queda
"reservado"); si la transacción no prospera, se **devuelve en ABORT**. Ventajas:
- Cualquier operación local concurrente ve el saldo reducido sin lógica extra.
- Bajo el `LockManager` por cuenta, el `leer-validar-restar` del PREPARE es atómico.
- El **destino** solo verifica en PREPARE que la cuenta existe; **suma en COMMIT**.

| Fase | Origen (DEBITO) | Destino (CREDITO) |
|---|---|---|
| PREPARE | valida saldo, **resta** (reserva), `PREPARED`, vota YES/NO | verifica que existe, `PREPARED`, vota YES/NO |
| COMMIT | nada (ya restó), `COMMITTED` | **suma**, `COMMITTED` |
| ABORT | **devuelve** el importe, `ABORTED` | nada, `ABORTED` |

---

## 2. Enrutado de una transferencia (`TransferenciaService.transferencia`)

```
                origen y destino
                       │
     ┌─────────────────┼──────────────────────┐
     ▼                 ▼                        ▼
 ambos en ESTE    mismo banco, pero        bancos distintos
   banco          remoto                        │
     │                 │                        ▼
 transferencia     reenviar al dueño     2PC (este banco = COORDINADOR)
 local (Hito 1)    (/api/.../transferencia)  prepare → commit | abort
```

El caso "mismo banco remoto" se **reenvía** al dueño (evita un 2PC sobre un único archivo con dos
prepares del mismo `txId`, que colisionarían en el registro).

---

## 3. Archivos creados (paquete `com.banco.coordinator` salvo indicación)

| Archivo | Rol |
|---|---|
| `model/RolParticipante.java` | enum `DEBITO` / `CREDITO` |
| `model/Voto.java` | enum `YES` / `NO` |
| `model/dto/PrepareRequest.java` | `{ txId, cuenta, rol, monto }` |
| `model/dto/VotoResponse.java` | `{ txId, voto, motivo }` + factorías `yes()/no()` |
| `model/dto/TxRequest.java` | `{ txId }` (cuerpo de commit/abort) |
| `coordinator/Registro2PC.java` | mapa en memoria `txId → ReservaPendiente`; permite COMMIT/ABORT **idempotentes** |
| `coordinator/TwoPhaseCommitParticipant.java` | lógica local de `prepare/commit/abort` bajo `LockManager` |
| `coordinator/TwoPhaseCommitCoordinator.java` | orquesta `prepare → decisión → commit|abort`; despacho local vs. remoto |
| `controller/CoordinacionController.java` | endpoints `POST /internal/2pc/{prepare,commit,abort}` |

## 4. Archivos modificados

| Archivo | Cambio |
|---|---|
| `service/TransferenciaService.java` | nuevo método `transferencia(...)` con el enrutado; `transferenciaLocal` intacto; `buscarLocal` ya no lanza 501 |
| `controller/OperacionController.java` | `/api/operaciones/transferencia` usa el enrutado y devuelve `COMMITTED`/`ABORTED` |
| `service/BancoRemotoClient.java` | + `Optional<String> urlDePeer(String bancoId)` (localizar un peer por id) |
| `service/CuentaService.java` | `buscarCuenta(...)` pasó a **público** (lo usa el participante) |
| `resources/data/cuentas-banco{A,B,C}.json` | **CREADOS** (faltaban; sin ellos la app no arrancaba). Cumplen las 5 condiciones |

---

## 5. Contrato de los endpoints internos

| Método | Ruta | Cuerpo | Respuesta / efecto |
|---|---|---|---|
| POST | `/internal/2pc/prepare` | `PrepareRequest` | `VotoResponse` (YES/NO) — valida y reserva |
| POST | `/internal/2pc/commit` | `{txId}` | aplica la reserva; idempotente |
| POST | `/internal/2pc/abort` | `{txId}` | libera la reserva; idempotente |

Despacho: si el participante es **este** banco → llamada directa a `TwoPhaseCommitParticipant`
(evita HTTP a sí mismo); si es **otro** → `BancoRemotoClient.postInternal(url, path, body, ...)`.

---

## 6. Verificación realizada (evidencia para el informe)

Levantados Banco A (8081) y Banco C (8083). Resultados reales:

| Escenario | Resultado | Comprobación |
|---|---|---|
| **COMMIT** `A-1001 → C-3001` (300) | `{"estado":"COMMITTED"}` | A-1001 1500→**1200**, C-3001 1750→**2050** |
| **ABORT** destino inexistente `C-9999` | `{"estado":"ABORTED"}` | A-1001 sigue **1200** (reserva **devuelta**) |
| **ABORT** saldo insuficiente (`A-1003` 250, pide 999999) | `{"estado":"ABORTED"}` | A-1003 sigue **250**, C-3001 sigue **2050** |
| **Vista global** C200 con Banco B apagado | 6 cuentas (3 A + 3 C) | tolera peer caído (Int.1) |

**Evidencia en archivo** — el **mismo `txId`** en ambos bancos:
```
Banco A: TX-b9eeb504 TRANSFERENCIA A-1001 -> null   300 COMMITTED
Banco C: TX-b9eeb504 TRANSFERENCIA null   -> C-3001 300 COMMITTED
Banco A: TX-a256bf7c TRANSFERENCIA A-1001 -> null   100 ABORTED   (destino inexistente)
Banco C: TX-447264cb TRANSFERENCIA null   -> C-3001 999999 ABORTED (origen votó NO)
```

**Evidencia en logs** (`@Slf4j`, capturar para el informe):
```
2PC COORDINADOR tx=TX-b9eeb504 inicia | A-1001 (BANCO_A) --300--> C-3001 (BANCO_C)
2PC PREPARE     tx=TX-b9eeb504 rol=DEBITO cuenta=A-1001 monto=300 -> YES (saldo tras reserva: 1200.00)
2PC COORDINADOR tx=TX-b9eeb504 voto de BANCO_A = YES
2PC COORDINADOR tx=TX-b9eeb504 voto de BANCO_C = YES
2PC COORDINADOR tx=TX-b9eeb504 DECISION=COMMIT
...
2PC COORDINADOR tx=TX-a256bf7c DECISION=ABORT (voto origen=YES, voto destino=NO)
2PC PREPARE     tx=TX-447264cb DEBITO cuenta=A-1003 monto=999999 -> NO (saldo insuficiente, saldo=250.00)
```

### Criterios de aceptación (todos ✅)
- [x] Transferencia A→C deja ambos archivos consistentes (una resta, otra suma).
- [x] Destino inexistente en PREPARE → abort global, ningún saldo cambia.
- [x] Origen sin saldo → vota NO → abort.
- [x] `Transaccion` con estados `PREPARED → COMMITTED`/`ABORTED` visibles en el archivo.
- [x] Logs muestran PREPARE / VOTO / COMMIT / ABORT.

---

## 7. Pendiente / refinamientos (siguiente iteración)

1. **Integración con Integrante 3 (exclusión mutua distribuida):** antes de reservar en PREPARE,
   llamar a `ExclusionMutua.adquirir(cuenta)` y liberar tras commit/abort (hay un `// TODO` marcado
   en `TwoPhaseCommitParticipant.commit`). Hoy la exclusión es solo local (`LockManager`).
2. **Integración con Integrante 4 (replicación):** tras `COMMITTED`, disparar
   `ReplicacionService.replicar(data)` (hay `// TODO` en `TwoPhaseCommitParticipant.commit`).
3. **Reintentos de la decisión** (COMMIT/ABORT) ante caída de un participante: hoy se loguea el
   fallo (`enviarDecision`); falta cola de reintento con backoff (se coordina con Int.4).
4. **Recuperación al arranque:** al iniciar, revisar transacciones en estado `PREPARED` huérfanas y
   resolverlas (consultar al coordinador). Opcional/bonus.
5. **Tests de integración** (Aporte B): 2PC commit, 2PC abort y dos transferencias concurrentes
   sobre `C200` serializadas.

---

## 8. Cómo reproducir la demo

> Requiere **JDK 21+** (el `pom.xml` fija `java.version=21`) y Maven. En la máquina de desarrollo
> se validó compilando y ejecutando con JDK 17 sobreescribiendo `-Djava.version=17`; en la entrega
> usar un JDK 21+ real.

```bash
# Terminal 1 — Banco A
cd banco-service
JAVA_HOME="/c/Program Files/Java/jdk-25" ./mvnw spring-boot:run -Dspring-boot.run.profiles=bancoA
# Terminal 2 — Banco C
JAVA_HOME="/c/Program Files/Java/jdk-25" ./mvnw spring-boot:run -Dspring-boot.run.profiles=bancoC

# Terminal 3 — pruebas
curl -X POST http://localhost:8081/api/operaciones/transferencia \
     -H "Content-Type: application/json" \
     -d '{"cuentaOrigen":"A-1001","cuentaDestino":"C-3001","monto":300}'   # -> COMMITTED
curl -X POST http://localhost:8081/api/operaciones/transferencia \
     -H "Content-Type: application/json" \
     -d '{"cuentaOrigen":"A-1001","cuentaDestino":"C-9999","monto":100}'   # -> ABORTED
# Ver los saldos y los archivos data/cuentas-banco{A,C}.json antes/después.
```
