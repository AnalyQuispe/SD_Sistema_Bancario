# Sistema Bancario Distribuido

Proyecto de Sistemas Distribuidos. Simula **3 entidades bancarias** (Banco A, B y C),
cada una como un **nodo independiente** que persiste sus cuentas en **archivos JSON locales**
(sin base de datos central). El mismo código `banco-service` se ejecuta 3 veces, una por
**perfil de Spring** (`bancoA`, `bancoB`, `bancoC`).

> Plan completo y rúbrica: ver [`PLAN_PROYECTO.md`](./PLAN_PROYECTO.md).
> El proyecto se divide en **Hito 1** (fundación local) e **Hito 2** (capa distribuida).

---

## Estado actual

### ✅ Hito 1 — Fundación local (backend): COMPLETO y verificado

| Criterio de aceptación (Hito 1) | Estado |
|---|---|
| Arrancan 3 instancias del mismo servicio con perfiles distintos | ✅ (perfiles bancoA/B/C, puertos 8081/8082/8083) |
| Cada banco carga sus cuentas desde su archivo JSON | ✅ (semilla en classpath → copia a `data/`) |
| Un depósito/retiro modifica el saldo y persiste en el archivo | ✅ (verificado con `curl`) |
| Una transferencia local es atómica (resta de una, suma a otra) | ✅ |
| Operaciones concurrentes sobre la misma cuenta no corrompen el saldo | ✅ (lock por número de cuenta) |
| El frontend muestra cuentas y permite operaciones locales | ⏳ **PENDIENTE** (ver abajo) |

### ⏳ Pendiente del Hito 1
- **Frontend React** (login por cliente, ver cuentas, depósito, retiro, transferencia local).
  El backend ya expone toda la API que el frontend necesita.

### 🔜 Hito 2 (aún no iniciado)
Comunicación banco-a-banco, transferencias distribuidas con **Two-Phase Commit**, elección de
coordinador, exclusión mutua distribuida, replicación de archivos, API Gateway y `docker-compose`
completo con frontend. El código ya deja "ganchos" para esto (estados `PREPARED`/`ABORTED`,
campo `bancoId` en `Cuenta`, mensaje 501 en transferencias hacia otro banco).

---

## Requisitos

- **JDK 21 o superior** (probado compilando con **JDK 25** al nivel de lenguaje 21).
- (Opcional) **Docker** + **Docker Compose** para levantar los 3 bancos de una vez.
- No hace falta instalar Maven: el proyecto trae el **Maven Wrapper** (`mvnw`).

### ⚠️ Nota sobre la versión de Java (importante para el grupo)

El `pom.xml` fija `<java.version>21</java.version>`. En esta máquina conviven varios JDK
(Java 8, 17 y 25) y, por defecto, el wrapper de Maven tomaba el **Java 8** del `JAVA_HOME`,
con lo que **no compilaba**. Solución: apuntar Maven a un JDK 21+ **solo para el comando**,
sin cambiar nada global:

```bash
# Bash / Git Bash
JAVA_HOME="/c/Program Files/Java/jdk-25" ./mvnw clean test
```

```powershell
# PowerShell
$env:JAVA_HOME="C:\Program Files\Java\jdk-25"; .\mvnw.cmd clean test
```

> Sirve cualquier JDK **21, 24 o 25** que tengas instalado; basta con que `JAVA_HOME`
> apunte a él. Con Java 8 o 17 **no** compila (el destino es Java 21).

---

## Cómo ejecutar

### Opción A — Una instancia local (desarrollo)

```bash
cd banco-service
JAVA_HOME="/c/Program Files/Java/jdk-25" ./mvnw spring-boot:run -Dspring-boot.run.profiles=bancoA
```

Cambia `bancoA` por `bancoB` o `bancoC` para arrancar otro nodo.
Cada perfil define su puerto y su archivo de datos:

| Perfil | Puerto | Archivo de datos | Semilla |
|---|---|---|---|
| `bancoA` | 8081 | `data/cuentas-bancoA.json` | `resources/data/cuentas-bancoA.json` |
| `bancoB` | 8082 | `data/cuentas-bancoB.json` | `resources/data/cuentas-bancoB.json` |
| `bancoC` | 8083 | `data/cuentas-bancoC.json` | `resources/data/cuentas-bancoC.json` |

> La primera vez, cada banco **copia su semilla** del classpath a la carpeta `data/`
> (externa al jar) y a partir de ahí trabaja sobre esa copia. Borra `banco-service/data/`
> para reiniciar los saldos.

### Opción B — Los 3 bancos con Docker

```bash
docker-compose up --build
```

Levanta `banco-a` (8081), `banco-b` (8082) y `banco-c` (8083), cada uno con su volumen de datos.

---

## API REST (Hito 1)

Base: `http://localhost:8081` (Banco A).

| Método | Ruta | Descripción |
|---|---|---|
| GET | `/api/banco` | Identidad del nodo (`id`, `nombre`) |
| GET | `/api/clientes/{id}/cuentas` | Cuentas del cliente **en este banco** |
| GET | `/api/cuentas/{numero}` | Saldo y datos de una cuenta |
| POST | `/api/operaciones/deposito` | `{ "cuenta": "A-1001", "monto": 200 }` |
| POST | `/api/operaciones/retiro` | `{ "cuenta": "A-1002", "monto": 50 }` |
| POST | `/api/operaciones/transferencia` | `{ "cuentaOrigen": "A-1001", "cuentaDestino": "A-1003", "monto": 100 }` |
| GET | `/actuator/health` | Salud (healthcheck) |

> Errores: `404` cuenta/cliente inexistente, `422` saldo insuficiente, `400` cuerpo inválido
> (montos ≤ 0). El cuerpo de error es JSON uniforme (ver `GlobalExceptionHandler`).
> Una transferencia hacia una cuenta de **otro** banco responde `501 Not Implemented`
> (se resolverá con 2PC en el Hito 2).

### Prueba rápida (con Banco A arrancado)

```bash
curl http://localhost:8081/api/clientes/C001/cuentas
curl -X POST http://localhost:8081/api/operaciones/deposito \
     -H "Content-Type: application/json" -d '{"cuenta":"A-1001","monto":200}'
curl -X POST http://localhost:8081/api/operaciones/transferencia \
     -H "Content-Type: application/json" \
     -d '{"cuentaOrigen":"A-1001","cuentaDestino":"A-1003","monto":100}'
```

---

## Cómo funciona el código (lógica del Hito 1)

```
controller/      → expone la API REST (sin lógica de negocio)
service/         → reglas de negocio + control de concurrencia
repository/      → lectura/escritura del archivo JSON (Java NIO + FileLock)
model/           → entidades (Cliente, Cuenta, Transaccion) y DTOs
config/          → BancoProperties (id, puerto, archivo) por perfil
exception/       → errores de dominio + traductor a respuestas HTTP
```

Flujo de una operación (p. ej. transferencia local):

```
POST /api/operaciones/transferencia
        │
   OperacionController
        │
   TransferenciaService ── LockManager.conLocks(origen, destino)  ← exclusión mutua local
        │                        (locks tomados en orden alfabético → sin deadlock)
        │   valida saldo, resta a origen, suma a destino, registra Transaccion
        │
   CuentaRepository.save()  ← escritura ATÓMICA con FileLock (NIO)
        │
   data/cuentas-bancoX.json (disco)
```

### Piezas clave y por qué

- **`CuentaRepository`** — Persistencia en archivo (requisito de la consigna):
  - Serializa con **Jackson**.
  - **Escritura atómica**: escribe en `*.tmp` y hace `ATOMIC_MOVE`; si falla a mitad, el JSON
    nunca queda corrupto.
  - **`FileLock` (Java NIO)**: lock **compartido** para leer y **exclusivo** para escribir,
    de modo que sea seguro incluso entre procesos distintos.
  - Mantiene una **copia en memoria** (`cache`) como fuente de verdad durante la ejecución.

- **`LockManager`** — Concurrencia local (criterio "operaciones concurrentes no corrompen el saldo"):
  - Un **`ReentrantLock` por número de cuenta** (no bloquea cuentas distintas).
  - Para operaciones con varias cuentas, **toma los locks en orden alfabético** → evita interbloqueos.

- **`CuentaService` / `TransferenciaService`** — Reglas de negocio (depósito, retiro, transferencia
  local). Cada escritura ocurre dentro del lock de la(s) cuenta(s) y persiste con el repositorio.
  Los importes usan **`BigDecimal`** (sin errores de redondeo).

- **Perfiles de Spring** (`application-bancoX.yml`) — El **mismo jar** se comporta como Banco A, B o C
  según el perfil: cambia `banco.id`, el puerto y el archivo de datos.

- **Datos semilla** (`resources/data/cuentas-bancoX.json`) — Cumplen las 5 condiciones de la consigna:
  - ≥3 clientes por banco y ≥3 cuentas por cliente.
  - Cliente exclusivo por banco: `C001` (A), `C002` (B), `C003`/`C004` (C).
  - Cliente en 2 bancos: `C100` (A y B).
  - Cliente en los 3 bancos: `C200` (A, B y C) — mismo `id` en los tres archivos.

> La **vista unificada** de las cuentas de `C100`/`C200` repartidas entre bancos es trabajo del
> **Hito 2** (cada banco hoy solo ve las suyas).

---

## Cómo revisar / probar (para el grupo)

1. **Compilar y pasar tests:**
   ```bash
   cd banco-service
   JAVA_HOME="/c/Program Files/Java/jdk-25" ./mvnw clean test
   ```
   Debe terminar en `BUILD SUCCESS` (1 test: `contextLoads`).

2. **Arrancar un banco y probar la API** con los `curl` de arriba; comprobar que tras un
   depósito/retiro/transferencia el archivo `banco-service/data/cuentas-bancoA.json` queda
   actualizado (la persistencia es el corazón del Hito 1).

3. **Probar el caso de error:** un retiro mayor al saldo debe devolver `422` y **no** modificar nada.

4. **(Opcional) Concurrencia:** lanzar varios depósitos en paralelo sobre la misma cuenta y
   verificar que la suma final es exacta (el `LockManager` los serializa).

---

## Estructura del repositorio

```
SD_Sistema_Bancario/
├── PLAN_PROYECTO.md          # Especificación completa y rúbrica
├── README.md                 # Este archivo
├── docker-compose.yml        # Levanta los 3 bancos
└── banco-service/            # Servicio Spring Boot (1 código, 3 instancias)
    ├── Dockerfile
    ├── pom.xml
    └── src/
        ├── main/java/com/banco/   # controller, service, repository, model, config, exception
        ├── main/resources/        # application*.yml + data/ (semillas JSON)
        └── test/                  # contextLoads + config de test
```
