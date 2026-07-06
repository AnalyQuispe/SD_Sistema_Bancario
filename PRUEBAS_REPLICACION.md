# Manual de Pruebas de Replicación y Tolerancia a Fallos (Integrante 4)

Este documento contiene la guía paso a paso para ejecutar y verificar la capa de replicación distribuida, reintentos asíncronos y restauración de base de datos desarrollada para el **Hito 2**.

---

## 🛠️ Requisitos Previos
1. Tener **Docker Desktop** instalado y en ejecución en tu máquina Windows.
2. Contar con una terminal compatible con PowerShell o CMD.
3. Tener **Python 3** instalado en tu sistema (necesario para ejecutar el script `killbank.py`).

---

## 🚀 Paso 1: Levantar el Ecosistema Completo
Abre tu consola en la raíz de este proyecto (`C:\Users\JUAN\Desktop\dfdf\SD_Sistema_Bancario`) y ejecuta:

```powershell
docker-compose up --build
```
*Deja esta terminal abierta*, ya que mostrará en tiempo real los logs generados por todas las instancias de los bancos y servicios.

---

## 🧪 Escenarios de Prueba

Abre una **segunda terminal** en el mismo directorio del proyecto para ejecutar las pruebas con `curl.exe`:

### Escenario A: Replicación Normal (Banco A $\rightarrow$ Banco B)
Este escenario comprueba que al realizar transacciones locales, el sistema las replica de forma asíncrona hacia el peer destino correspondiente.

1.  **Ejecutar un depósito en el Banco A (puerto 8081)**:
    ```powershell
    curl.exe -X POST http://localhost:8081/api/operaciones/deposito -H "Content-Type: application/json" -d '{\"cuenta\":\"A-1001\",\"monto\":150.00}'
    ```
2.  **Verificar el estado de las réplicas en el Banco B (puerto 8082)**:
    ```powershell
    curl.exe http://localhost:8082/internal/replica/estado -H "X-Internal-Token: sd-bancos-2026-secreto-interno"
    ```
    *Debe retornar:* `{"BANCO_A":1}` *(indica que Banco B tiene guardada la versión 1 de la réplica de Banco A).*
3.  **Comprobar el contenido de la réplica en Banco B**:
    ```powershell
    curl.exe http://localhost:8082/internal/replica/BANCO_A -H "X-Internal-Token: sd-bancos-2026-secreto-interno"
    ```
    *Retornará el JSON completo de Banco A, donde el saldo de la cuenta `A-1001` ya refleja los $1650.00 USD.*

---

### Escenario B: Tolerancia a Fallos (Banco B Caído)
Este escenario comprueba que si el peer de destino está caído, las réplicas se encolan y se envían de forma automática cuando el peer resucita.

1.  **Apagar el Banco B**:
    ```powershell
    python Scrips_de_Demostracion/killbank.py --B
    ```
2.  **Efectuar una nueva transacción en el Banco A**:
    ```powershell
    curl.exe -X POST http://localhost:8081/api/operaciones/deposito -H "Content-Type: application/json" -d '{\"cuenta\":\"A-1001\",\"monto\":100.00}'
    ```
    *La llamada responderá con éxito inmediatamente. En los logs de `banco-a` verás avisos de reintentos y el mensaje de encolamiento.*
3.  **Encender nuevamente el Banco B**:
    ```powershell
    python Scrips_de_Demostracion/killbank.py --B --up
    ```
4.  **Verificar que la réplica pendiente fue procesada**:
    *Espera unos 10 segundos para dar tiempo al programador de reintentar y ejecuta:*
    ```powershell
    curl.exe http://localhost:8082/internal/replica/estado -H "X-Internal-Token: sd-bancos-2026-secreto-interno"
    ```
    *El estado de la versión debe haber subido a:* `{"BANCO_A":2}`

---

### Escenario C: Recuperación ante Desastres (Restauración)
Este escenario comprueba la capacidad de un banco para reconstruirse pidiendo su réplica a los peers vivos de la red en caso de que su base de datos local sea destruida.

1.  **Eliminar la base de datos física de Banco A**:
    ```powershell
    docker exec banco-a rm /app/data/cuentas-bancoA.json
    ```
2.  **Reiniciar el contenedor de Banco A** (arrancará en su estado semilla por defecto, con saldo semilla de $1500.00 USD en `A-1001`):
    ```powershell
    docker restart banco-a
    ```
3.  **Solicitar restauración desde réplicas de peers**:
    ```powershell
    curl.exe -X POST http://localhost:8081/internal/replica/restaurar -H "X-Internal-Token: sd-bancos-2026-secreto-interno"
    ```
4.  **Confirmar restauración de saldos**:
    ```powershell
    curl.exe http://localhost:8081/api/cuentas/A-1001
    ```
    *El saldo de la cuenta `A-1001` debe haber regresado exitosamente a $1750.00 USD, recuperando los depósitos realizados antes de la destrucción.*

---

## 📁 Archivos Relacionados con la Implementación

Toda la lógica de esta solución se encuentra distribuida en los siguientes archivos clave del código:
*   [ReplicacionService.java](file:///C:/Users/JUAN/Desktop/dfdf/SD_Sistema_Bancario/banco-service/src/main/java/com/banco/service/ReplicacionService.java): Núcleo de lógica asíncrona, reintentos, colas y restauración.
*   [ReplicacionController.java](file:///C:/Users/JUAN/Desktop/dfdf/SD_Sistema_Bancario/banco-service/src/main/java/com/banco/controller/ReplicacionController.java): Endpoints de comunicación REST protegidos.
*   [BancoDataChangedEvent.java](file:///C:/Users/JUAN/Desktop/dfdf/SD_Sistema_Bancario/banco-service/src/main/java/com/banco/event/BancoDataChangedEvent.java): Evento para desacoplar escrituras en el repositorio de la replicación.
*   [CuentaRepository.java](file:///C:/Users/JUAN/Desktop/dfdf/SD_Sistema_Bancario/banco-service/src/main/java/com/banco/repository/CuentaRepository.java): Incremento de versión automático y publicación de eventos.
*   [BancoData.java](file:///C:/Users/JUAN/Desktop/dfdf/SD_Sistema_Bancario/banco-service/src/main/java/com/banco/model/BancoData.java): Estructura de datos que almacena la propiedad `version`.
