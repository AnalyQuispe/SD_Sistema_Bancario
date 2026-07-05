# ==============================================================================
# DEMO: Exclusión Mutua Distribuida Ricart-Agrawala (Integrante 3)
# ==============================================================================
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host "Iniciando Demo de Exclusion Mutua Distribuida (Ricart-Agrawala)" -ForegroundColor Cyan
Write-Host "Prueba: 2 transferencias concurrentes sobre la cuenta C200" -ForegroundColor Cyan
Write-Host "===========================================================" -ForegroundColor Cyan
Write-Host ""

# 1. Obtener Token de Autenticacion JWT
Write-Host "1. Obteniendo Token JWT para el cliente C200..." -ForegroundColor Yellow
$loginResponse = curl.exe -s -X POST http://localhost:8080/auth/login -H "Content-Type: application/json" -d '{\"clienteId\":\"C200\"}'
$token = ($loginResponse | ConvertFrom-Json).token
Write-Host "Token obtenido correctamente." -ForegroundColor Green
Write-Host ""

Write-Host "2. Lanzando DOS transferencias concurrentes al mismo milisegundo..." -ForegroundColor Yellow
Write-Host "   - Transferencia 1: Banco A (A-2001) -> Banco C (C-3001)" -ForegroundColor DarkGray
Write-Host "   - Transferencia 2: Banco B (B-2001) -> Banco C (C-3001)" -ForegroundColor DarkGray
Write-Host "   (Ambas tocaran la cuenta destino C-3001, forzando competencia por el Lock Distribuido)" -ForegroundColor DarkGray
Write-Host ""

# Ejecutar transferencias concurrentemente como Jobs de PowerShell
$job1 = Start-Job {
    param($token)
    curl.exe -s -X POST http://localhost:8080/a/api/operaciones/transferencia -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d "{\`"cuentaOrigen\`":\`"A-2001\`",\`"cuentaDestino\`":\`"C-3001\`",\`"monto\`":10}"
} -ArgumentList $token

$job2 = Start-Job {
    param($token)
    curl.exe -s -X POST http://localhost:8080/b/api/operaciones/transferencia -H "Authorization: Bearer $token" -H "Content-Type: application/json" -d "{\`"cuentaOrigen\`":\`"B-2001\`",\`"cuentaDestino\`":\`"C-3001\`",\`"monto\`":10}"
} -ArgumentList $token

# Esperar a que terminen
Wait-Job $job1, $job2 | Out-Null

$res1 = Receive-Job $job1
$res2 = Receive-Job $job2

Write-Host "Resultados de las transferencias:" -ForegroundColor Yellow
Write-Host "Transferencia 1: $res1" -ForegroundColor Green
Write-Host "Transferencia 2: $res2" -ForegroundColor Green
Write-Host ""

# 3. Mostrar Evidencia en Logs
Write-Host "3. Evidencia en los logs del BANCO_C (que recibio peticiones de ambos al mismo tiempo):" -ForegroundColor Yellow
Write-Host "Buscando trazas de EXCLUSION MUTUA..." -ForegroundColor Yellow
Start-Sleep -Seconds 2
docker logs banco-c | Select-String "EXCLUSION MUTUA" | Select-Object -Last 15
Write-Host ""
Write-Host "Debes observar que un banco adquirio el lock, entro a SC, y el otro fue ENCOLADO hasta que el primero emitio el LIBERADO." -ForegroundColor Cyan
Write-Host "FIN DE LA DEMO." -ForegroundColor Cyan
