# Diagramas PlantUML — Funcionalidad del Sistema Bancario Distribuido

Colección para **entender cómo funciona** el proyecto. Renderiza cada uno en
**https://www.plantuml.com/plantuml/uml** (pega el código → guarda el PNG).

Orden: de lo más general (arquitectura, recorrido de una petición) a lo más específico
(2PC, elección, exclusión mutua, replicación, estados).

---

## 1. Arquitectura general (¿qué piezas hay y cómo se conectan?)

```plantuml
@startuml arquitectura
skinparam backgroundColor White
skinparam componentStyle rectangle
skinparam shadowing false
actor "Cliente\n(navegador)" as U
rectangle "Frontend React\n(nginx :5173)" as FE #EEEEEE
rectangle "API Gateway\n(:8080) JWT + enrutado" as GW #D5F5E3
rectangle "Banco A :8081" as A #EBF5FB
rectangle "Banco B :8082" as B #EBF5FB
rectangle "Banco C :8083" as C #EBF5FB
database "cuentas-bancoA.json" as DA
database "cuentas-bancoB.json" as DB
database "cuentas-bancoC.json" as DC
U --> FE
FE --> GW : /api/** (con JWT)
GW --> A
GW --> B
GW --> C
A <--> B : /internal/**
B <--> C : /internal/**
C <--> A : /internal/**
A --> DA
B --> DB
C --> DC
note bottom of GW : bloquea /internal/** y valida el token
@enduml
```

---

## 2. Recorrido de una petición de punta a punta (¿por dónde pasa una operación?)

```plantuml
@startuml recorrido
skinparam backgroundColor White
actor Usuario
participant "Frontend" as FE
participant "API Gateway" as GW
participant "Banco A" as A
database "archivo JSON" as F
Usuario -> FE : opera (sesión iniciada)
FE -> GW : POST /a/api/operaciones/... \n(Authorization: Bearer JWT)
GW -> GW : valida JWT + RBAC
GW -> A : reenvía a /api/operaciones/...
A -> A : LockManager: lock por cuenta
A -> F : lee/escribe (FileLock + escritura atómica)
A --> GW : respuesta (saldo / estado)
GW --> FE : respuesta
FE --> Usuario : muestra el resultado
note over GW : sin token válido -> 401\nruta /internal/** -> 403
@enduml
```

---

## 3. Decisión de una transferencia (¿cómo elige el sistema qué hacer?)

```plantuml
@startuml decision-transferencia
skinparam backgroundColor White
start
:recibe transferencia(origen, destino, monto);
if (¿origen y destino en ESTE banco?) then (sí)
  :transferencia LOCAL atómica\n(retiro + depósito, Hito 1);
  :COMMITTED;
else (no)
  if (¿ambas en el MISMO otro banco?) then (sí)
    :reenvía al banco dueño\n(evita 2PC innecesario);
  else (no: bancos distintos)
    :Two-Phase Commit\n(este banco = COORDINADOR);
  endif
endif
stop
@enduml
```

---

## 4. Vista global de cuentas (¿cómo ve el cliente sus 9 cuentas desde un solo banco?)

```plantuml
@startuml vista-global
skinparam backgroundColor White
participant "Banco A\n(consultado)" as A
participant "Banco B" as B
participant "Banco C" as C
A -> A : busca cuentas locales de C200 (3)
A -> B : GET /internal/clientes/C200/cuentas-locales
B --> A : 3 cuentas
A -> C : GET /internal/clientes/C200/cuentas-locales
C --> A : 3 cuentas
A -> A : une 3 + 3 + 3 = 9 cuentas
note over A #FADBD8 : si un peer NO responde,\ndevuelve las cuentas de los vivos\n(no falla: tolerancia a fallos)
@enduml
```

---

## 5. Two-Phase Commit — commit y abort (¿cómo es atómica una transferencia entre bancos?)

```plantuml
@startuml 2pc
skinparam backgroundColor White
skinparam sequenceMessageAlign center
participant "Coordinador\n(Banco A)" as Coord #D5F5E3
participant "ORIGEN\n(A, DEBITO)" as O #EBF5FB
participant "DESTINO\n(C, CREDITO)" as D #FDEBD0
Coord -> Coord : genera txId, toma locks distribuidos
== PREPARE ==
Coord -> O : PREPARE(txId, A-1200, 300)
O -> O : valida saldo, RESERVA 5000->4700, PREPARED
O --> Coord : VOTO YES
Coord -> D : PREPARE(txId, C-3200, 300)
D -> D : la cuenta existe, PREPARED
D --> Coord : VOTO YES
== DECISION ==
alt ambos YES -> COMMIT
  Coord -> O : COMMIT
  O -> O : (ya restó) COMMITTED
  Coord -> D : COMMIT
  D -> D : SUMA 4800->5100, COMMITTED
  note over Coord,D #D5F5E3 : mismo txId COMMITTED en ambos archivos
else algún NO / timeout -> ABORT
  Coord -> O : ABORT
  O -> O : DEVUELVE 4700->5000, ABORTED
  Coord -> D : ABORT (no-op)
  note over Coord,D #FADBD8 : ningún saldo cambia
end
Coord -> Coord : libera locks distribuidos
@enduml
```

---

## 6. Estados de una transacción (¿por qué estados pasa un txId?)

```plantuml
@startuml estados-tx
skinparam backgroundColor White
[*] --> PENDING : se crea el txId
PENDING --> PREPARED : PREPARE (valida + reserva)
PREPARED --> COMMITTED : todos votaron YES -> COMMIT
PREPARED --> ABORTED : algún NO / timeout -> ABORT
COMMITTED --> [*]
ABORTED --> [*]
note right of PREPARED : el estado se PERSISTE en el archivo\n(evidencia y recuperación)
@enduml
```

---

## 7. Elección de coordinador — Bully (¿quién manda si cae un nodo?)

```plantuml
@startuml bully
skinparam backgroundColor White
participant "Banco A (1)" as A #EBF5FB
participant "Banco B (2)" as B #FDEBD0
participant "Banco C (3)" as C #E8F8F5
C -> C : <font color=red>CAE
destroy C
A -> A : iniciarEleccion()
A -->x C : ELECCION
note right of A #FADBD8 : C no responde
A -> B : ELECCION
B --> A : OK (soy mayor y vivo)
B -->x C : ELECCION
note right of B #FADBD8 : C tampoco responde
B -> B : se proclama COORDINADOR
B -> A : COORDINADOR = Banco B
note over A,B #D5F5E3 : nuevo coordinador: BANCO B
@enduml
```

---

## 8. Exclusión mutua — Ricart–Agrawala (¿cómo no se corrompe una cuenta compartida?)

```plantuml
@startuml ricart
skinparam backgroundColor White
participant "Banco A" as A #EBF5FB
participant "Banco B" as B #FDEBD0
A -> A : ts=5, SOLICITANDO C-3200
B -> B : ts=7, SOLICITANDO C-3200
A -->> B : REQUEST(A, C-3200, 5)
B -->> A : REQUEST(B, C-3200, 7)
note left of A #FADBD8 : mi ts (5) < 7\n=> ENCOLA a B (OK diferido)
note right of B #D5F5E3 : ts de A (5) < mi 7\n=> OK inmediato
B --> A : OK
hnote over A #D5F5E3 : SECCIÓN CRÍTICA (opera C-3200)
... B espera el OK diferido ...
A -> A : LIBERA
A -->> B : OK diferido
hnote over B #FDEBD0 : SECCIÓN CRÍTICA (opera C-3200)
note over A,B : las 2 operaciones se SERIALIZAN\n(saldo final exacto)
@enduml
```

---

## 9. Replicación y tolerancia a fallos (¿cómo se respaldan los archivos y qué pasa si cae un nodo?)

```plantuml
@startuml replicacion
skinparam backgroundColor White
participant "Banco A" as A #EBF5FB
participant "Banco B\n(réplica de A)" as B #FDEBD0
A -> A : commit -> saldo cambia, version++
A ->> B : POST /internal/replicar (archivo, version N)
alt B vivo
  B -> B : version N > guardada -> guarda copia
  B --> A : OK
else B caído
  A -> A : 3 reintentos con backoff -> ENCOLA
  ... cada 10 s reintenta ...
  A ->> B : reintento cuando B vuelve
  B --> A : OK (réplica recuperada)
end
note over A,B : la operación del usuario NUNCA falla por la réplica
@enduml
```
