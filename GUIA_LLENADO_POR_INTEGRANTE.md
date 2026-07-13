# Guía de llenado de los entregables por integrante

Reparto de trabajo para completar los dos entregables escritos del proyecto:

- **Informe** (`INFORME_Sistema_Bancario_Distribuido.docx`) — 14 capturas.
- **Artículo IEEE** (`ARTICULO_LATEX_CORREGIDO.md` → compilar en Overleaf) — 7 figuras.

**Asignación de integrantes:**

| Nº | Integrante | Rol (Hito 2) |
|----|------------|--------------|
| 1 | **Garlet** | Comunicación entre bancos y vista global |
| 2 | **Wilson** | Hito 1 (fundación local) + Two-Phase Commit |
| 3 | **Jhastyn** | Elección de coordinador y exclusión mutua |
| 4 | **Juan** | Replicación de archivos y tolerancia a fallos |
| 5 | **Jeanpiero** | API Gateway, frontend, despliegue y **edición final** |

> **Flujo general para todos:** (1) revisar/ajustar el texto de sus secciones, (2) ejecutar los comandos de sus capturas con el sistema levantado (`docker compose up --build`), (3) pegar la imagen en el lugar indicado.

---

# Anexo A — Guía de llenado del **INFORME** (.docx)

> El texto de todas las secciones ya está redactado. Cada integrante revisa sus secciones, ejecuta los comandos de sus capturas y pega la imagen debajo del recuadro naranja correspondiente.

## Resumen de asignación (informe)

| Integrante | Secciones del informe | Capturas |
|---|---|---|
| **Garlet** (Int. 1) | §2 (revisión), §3 (backend) | 5 |
| **Wilson** (Int. 2) | §3 (operaciones locales), §6 | **2, 3**, 7, 8, 9, 10 |
| **Jhastyn** (Int. 3) | §5, §7.2 (sus demos) | 13, 14 |
| **Juan** (Int. 4) | §4, §7.2 (killbank) | 11, 12 |
| **Jeanpiero** (Int. 5) | Carátula, §1, §2, §3 (gateway/frontend/docker), §7.1, §9, §10 y edición final | 1, 4, 6 |

## Detalle por integrante (informe)

### Wilson — Integrante 2 (Hito 1 + 2PC)
- **§3 Implementación:** revisar la parte de operaciones locales (depósito, retiro, transferencia local) y la tabla de la API.
- **§6 Transacciones y control de concurrencia:** revisar el diagrama del protocolo 2PC, el débito tentativo, la idempotencia y el registro persistente.
- **CAPTURA 2 (Hito 1):** depósito, retiro y transferencia local sobre A-1001/A-1002 con los saldos cambiando en cada paso, y el retiro excesivo respondiendo **HTTP 422** sin modificar nada.
- **CAPTURA 3 (Hito 1):** el archivo `/app/data/cuentas-bancoA.json` dentro del contenedor con los saldos actualizados y las transacciones DEPOSITO/RETIRO/TRANSFERENCIA registradas: la persistencia es en archivos, sin base de datos.
- **CAPTURA 7:** transferencia distribuida `A-1200 → C-3200` (monto 300): saldos antes, respuesta `COMMITTED` y saldos después (4700 / 5100).
- **CAPTURA 8:** logs `2PC` en banco-a y banco-c con el **mismo txId**: PREPARE → votos YES → DECISION=COMMIT.
- **CAPTURA 9:** los dos casos ABORT (cuenta inexistente y saldo insuficiente) con los saldos intactos.
- **CAPTURA 10:** historial de transacciones con txId y estados COMMITTED/ABORTED.

### Garlet — Integrante 1
- **§2 Diseño:** revisar el diagrama de arquitectura y la parte de comunicación entre bancos (peers, endpoints `/internal/**`, convención de prefijos A-/B-/C-).
- **§3 Implementación:** revisar la descripción del backend (capas, `BancoRemotoClient`, endpoint `cuentas-locales` que evita la recursión infinita).
- **CAPTURA 5:** login por `curl` para obtener el token y consulta de la vista global de C200 vía gateway: el JWT y las **9 cuentas** (3 de cada banco) preguntando solo al Banco A.

### Jhastyn — Integrante 3
- **§5 Coordinación y acuerdo:** revisar la descripción del Bully (prioridades A=1, B=2, C=3) y de Ricart–Agrawala (timestamps de Lamport, OK diferido, desempate por id).
- **§7.2:** revisar la mención de `demo_eleccion.ps1` y `demo_exclusion_mutua.ps1`.
- **CAPTURA 13:** tumbar el Banco C, forzar elección y mostrar en logs que **B se proclama COORDINADOR**; la consulta del coordinador devuelve BANCO_B. Al final revivir C.
- **CAPTURA 14:** dos transferencias concurrentes hacia C-3200: logs `EXCLUSION MUTUA` con un banco ENCOLADO hasta el OK diferido y saldo final exacto (+200).

### Juan — Integrante 4
- **§4 Sistema de archivos distribuidos:** revisar escritura atómica, anillo A→B→C→A, versionado y restauración.
- **§7.2:** revisar la mención de `killbank.py`.
- **CAPTURA 11:** estado de réplicas en el Banco B (versión de BANCO_A), logs de «Replicación exitosa» / «Réplica aplicada» y el archivo en `/app/data/replicas/` de B.
- **CAPTURA 12:** tumbar B, depósito en A (responde OK igual), logs con los 3 reintentos y «Encolando»; revivir B y mostrar «Replicación de pendiente exitosa».

### Jeanpiero — Integrante 5 (editor del informe)
- **Carátula:** completar los nombres de todos en la tabla de integrantes.
- **§1 Introducción, §9 Conclusiones, §10 Referencias:** revisión final de redacción.
- **§2 Diseño y §3 Implementación:** las partes de gateway, frontend React y Docker Compose.
- **§7.1 Seguridad:** JWT, RBAC y secreto `X-Internal-Token`.
- **CAPTURA 1:** `docker compose up --build` y `docker compose ps` con los 5 contenedores Up (healthy).
- **CAPTURA 4:** frontend en `http://localhost:5173`, login como C200 y dashboard con las 9 cuentas + indicador de bancos vivos.
- **CAPTURA 6:** el 401 sin token en el gateway y el 403 del endpoint interno sin secreto.
- **Cierre:** verificar formato, numeración de capturas y exportar el documento final.

## Orden recomendado de la sesión de capturas (informe)
Una sola sesión con el sistema levantado, en este orden (evita que unas demos ensucien a otras):

**1 → 2 → 3** (Hito 1) → **4 → 5 → 6** (frontend y seguridad) → **7 → 8 → 9 → 10** (2PC) → **11** (réplica) → **12** (cae B) → **13** (cae C) → **14** (concurrencia, con los 3 bancos vivos otra vez).

Para los saldos semilla exactos de las capturas 2 y 7–9, reiniciar antes con `docker compose down -v`.

---

# Anexo B — Guía de llenado del **ARTÍCULO** (LaTeX/IEEE)

> El texto ya está redactado. Cada integrante: (1) completa su bloque de autor, (2) revisa el texto de su sección, (3) produce sus figuras — las marcas **rojas** `[captura de ...]` son capturas reales del sistema y las **azules** `[imagen de ...]` son diagramas a generar con IA usando el prompt incluido en la propia marca, y (4) verifica sus filas de la Tabla I con los valores reales obtenidos.

## Resumen de asignación (artículo)

| Integrante | Secciones | Figuras | Filas de la Tabla I |
|---|---|---|---|
| **Garlet** (Int. 1) | §III Comunicación y vista global; revisar §II | Fig. 2 (captura vista global) | Vista global C200 / C100 / con C caído |
| **Wilson** (Int. 2) | §IV Two-Phase Commit | Fig. 3 (captura traza 2PC) | 2PC commit + saldos, 2PC abort ×2 |
| **Jhastyn** (Int. 3) | §V Coordinación y acuerdo | Fig. 4 (imagen IA: secuencia Ricart–Agrawala), Fig. 7 (captura 6 concurrentes) | 6 concurrentes + saldo, elección ×2 |
| **Juan** (Int. 4) | §VI Archivos distribuidos y tolerancia | Fig. 5 (captura réplica encolada/entregada) | Depósito con réplica caída, reintento |
| **Jeanpiero** (Int. 5, editor) | Resumen, §I, §II, §VII Seguridad, §VIII Conclusiones; compilación final en Overleaf | Fig. 1 (imagen IA: arquitectura), Fig. 6 (captura frontend) | 401, RBAC 403, /internal 403 |

## Detalle por integrante (artículo)

### Garlet — Integrante 1
- Completar su bloque `\IEEEauthorblockN/A` (nombre, universidad, correo).
- Revisar §II (planos `/api` vs `/internal`, convención A-/B-/C-) y §III completo.
- **Fig. 2:** `curl -s -H "Authorization: Bearer <TOKEN>" http://localhost:8080/a/api/clientes/C200/cuentas` → captura del JSON con las 9 cuentas y sus `bancoId`.
- **Tabla I:** C200 = 9 cuentas; C100 = 6; con C caído (`killbank.py --C`) C200 = 6 cuentas y HTTP 200. Revivir C al terminar.

### Wilson — Integrante 2
- Completar su bloque de autor.
- Revisar §IV (el párrafo del débito tentativo ya está corregido en la versión LaTeX final).
- **Fig. 3:** ejecutar el 2PC de **100** (¡no 300!) y capturar logs + archivos:
  ```bash
  curl -s -X POST http://localhost:8080/a/api/operaciones/transferencia -H "Authorization: Bearer <TOKEN>" -H "Content-Type: application/json" -d "{\"cuentaOrigen\":\"A-1200\",\"cuentaDestino\":\"C-3200\",\"monto\":100}"
  docker logs banco-a 2>&1 | findstr "2PC"
  docker logs banco-c 2>&1 | findstr "2PC"
  ```
- **Tabla I:** verificar 5000→4900 y 4800→4900; los dos aborts (destino `C-9999` y monto 999999 desde `A-1202`) con saldos intactos.

### Jhastyn — Integrante 3
- Completar su bloque de autor. Revisar §V.
- **Fig. 4:** generar el diagrama de secuencia con IA usando el prompt de la marca azul e insertarlo.
- **Fig. 7 (6 transferencias concurrentes desde B-2200):** ejecutar en PowerShell **después** del 2PC de Wilson:
  ```powershell
  $t = (curl.exe -s -X POST http://localhost:8080/auth/login -H "Content-Type: application/json" -d '{\"clienteId\":\"C200\"}' | ConvertFrom-Json).token
  $jobs = 1..6 | ForEach-Object {
    $d = if ($_ -le 3) { "A-1200" } else { "C-3200" }
    Start-Job { param($t,$d) curl.exe -s -X POST http://localhost:8080/b/api/operaciones/transferencia -H "Authorization: Bearer $t" -H "Content-Type: application/json" -d ('{\"cuentaOrigen\":\"B-2200\",\"cuentaDestino\":\"' + $d + '\",\"monto\":50}') } -ArgumentList $t,$d
  }
  Receive-Job $jobs -Wait
  curl.exe -s http://localhost:8082/api/cuentas/B-2200; curl.exe -s http://localhost:8081/api/cuentas/A-1200; curl.exe -s http://localhost:8083/api/cuentas/C-3200
  ```
- **Tabla I:** 6 COMMITTED y saldos B-2200=3900, A-1200=5050, C-3200=5050. **Elección:** `killbank.py --C`, forzar elección en A, logs → coordinador B; con los 3 vivos → C.

### Juan — Integrante 4
- Completar su bloque de autor. Revisar §VI.
- **Fig. 5:** `killbank.py --C` (C es la réplica de B), depósito en B, logs de B con reintentos y «Encolando»; `killbank.py --C --up`, log «Replicación de pendiente exitosa. Versión N». **Reemplazar «versión N» por el número real** en la figura y en la Tabla I.

### Jeanpiero — Integrante 5 (editor)
- Completar su bloque de autor y coordinar que los 5 estén completos.
- Revisar Resumen, §I, §II, §VII y §VIII.
- **Fig. 1:** generar el diagrama de arquitectura con IA usando el prompt de la marca azul.
- **Fig. 6:** captura del dashboard del frontend con sesión de C200 (9 cuentas + semáforo de bancos).
- **Tabla I:**
  - `curl -i http://localhost:8080/a/api/cuentas/A-1001` → **401**
  - `curl -i -H "Authorization: Bearer <TOKEN_C200>" http://localhost:8080/a/api/clientes/C001/cuentas` → **403**
  - `curl -i -X POST http://localhost:8081/internal/2pc/prepare -H "Content-Type: application/json" -d "{}"` → **403**
- **Cierre:** compilar en Overleaf (pdflatex), corregir desbordes (si la Tabla I desborda: `p{4.9cm}`), verificar que no quede ninguna marca roja/azul sin reemplazar.

## Orden de la sesión de capturas del artículo
Sesión **independiente de la del informe** (los números de la Tabla I dependen de ello):

`docker compose down -v && docker compose up --build` →
1. Vista global (Garlet)
2. 2PC 100 + aborts (Wilson)
3. 6 concurrentes (Jhastyn)
4. Seguridad 401/403/interno (Jeanpiero)
5. Frontend (Jeanpiero)
6. `killbank.py --C`: vista global 6 cuentas (Garlet), elección → B (Jhastyn), depósito en B con réplica caída (Juan)
7. `--C --up`: reintento de réplica (Juan) y re-elección → C (Jhastyn)

---

## Notas importantes

- **Dos sesiones de capturas distintas:** el informe usa monto **300** en el 2PC; el artículo usa **100** (para que la Tabla I cuadre: 4900/4900, 5050/5050/3900). No reutilizar capturas entre ambos.
- **Figuras con IA** (marcas azules del artículo): Fig. 1 (arquitectura) y Fig. 4 (secuencia Ricart–Agrawala). El prompt exacto está escrito dentro de cada marca `[imagen de ...]` en el LaTeX.
- **La prueba de 6 transferencias concurrentes** no tiene script en el repo; usar el comando PowerShell de arriba (Jhastyn).
