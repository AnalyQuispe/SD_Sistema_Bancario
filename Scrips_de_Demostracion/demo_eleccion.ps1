# ==============================================================================
# DEMO: Algoritmo de Elección Bully (Integrante 3)
# ==============================================================================
Write-Host "===================================================" -ForegroundColor Cyan
Write-Host "Iniciando Demo de Eleccion de Coordinador (Bully)" -ForegroundColor Cyan
Write-Host "Prioridades: BANCO_A = 1, BANCO_B = 2, BANCO_C = 3" -ForegroundColor Cyan
Write-Host "===================================================" -ForegroundColor Cyan
Write-Host ""

# 1. Consultar coordinador actual
Write-Host "1. Consultando coordinador actual (deberia ser BANCO_C si esta vivo, o B si C esta caido):" -ForegroundColor Yellow
$response = curl.exe -s -H "X-Internal-Token: sd-bancos-2026-secreto-interno" http://localhost:8081/internal/coordinador
Write-Host "Respuesta: $response" -ForegroundColor Green
Write-Host ""

# 2. Forzar la caida de BANCO_C y BANCO_B
Write-Host "2. Apagando BANCO_C y BANCO_B para forzar que BANCO_A se proclame coordinador..." -ForegroundColor Yellow
docker stop banco-c banco-b | Out-Null
Start-Sleep -Seconds 3
Write-Host "Bancos apagados." -ForegroundColor Green
Write-Host ""

# 3. Forzar eleccion desde BANCO_A
Write-Host "3. BANCO_A (prioridad 1) detecta fallo e inicia eleccion..." -ForegroundColor Yellow
$response = curl.exe -s -X POST -H "X-Internal-Token: sd-bancos-2026-secreto-interno" http://localhost:8081/internal/eleccion/forzar
Write-Host "Respuesta de Eleccion: $response" -ForegroundColor Green
Write-Host ""

# 4. Mostrar los logs de BANCO_A para ver la evidencia
Write-Host "4. Evidencia en los logs de BANCO_A:" -ForegroundColor Yellow
docker logs banco-a | Select-String "ELECCION" | Select-Object -Last 10
Write-Host ""

# 5. Recuperar los bancos
Write-Host "5. Reencendiendo BANCO_C y BANCO_B para volver a la normalidad..." -ForegroundColor Yellow
docker start banco-c banco-b | Out-Null
Start-Sleep -Seconds 8
Write-Host "Bancos reencendidos." -ForegroundColor Green
Write-Host ""

# 6. Consultar coordinador nuevamente
Write-Host "6. Consultando coordinador tras recuperacion (BANCO_C debio retomar el control al arrancar):" -ForegroundColor Yellow
$response = curl.exe -s -H "X-Internal-Token: sd-bancos-2026-secreto-interno" http://localhost:8081/internal/coordinador
Write-Host "Respuesta: $response" -ForegroundColor Green
Write-Host ""
Write-Host "FIN DE LA DEMO." -ForegroundColor Cyan
