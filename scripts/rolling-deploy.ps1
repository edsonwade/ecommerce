<#
.SYNOPSIS
  Rolling update sem downtime de um serviço do caminho crítico.
  Runbook: .claude/skills/ha-zero-downtime-deploy/SKILL.md
.EXAMPLE
  .\scripts\rolling-deploy.ps1 -Service order-service
  .\scripts\rolling-deploy.ps1 -Service frontend -WithInfraHA
#>
param(
    [Parameter(Mandatory)]
    [ValidateSet('frontend','gateway-api-service','authentication-service',
                 'product-service','cart-service','order-service','payment-service')]
    [string]$Service,
    [switch]$WithInfraHA
)
$ErrorActionPreference = 'Stop'

$composeFiles = @('-f','docker-compose.yml')
if ($WithInfraHA) { $composeFiles += @('-f','docker-compose.ha.yml') }
$composeFiles += @('-f','docker-compose.scale.yml')

# Nome da app no Eureka (frontend não regista). GATEWAY-SERVICE confirmado live 2026-07-05.
$eurekaNames = @{
    'gateway-api-service'    = 'GATEWAY-SERVICE'
    'authentication-service' = 'AUTHENTICATION-SERVICE'
    'product-service'        = 'PRODUCT-SERVICE'
    'cart-service'           = 'CART-SERVICE'
    'order-service'          = 'ORDER-SERVICE'
    'payment-service'        = 'PAYMENT-SERVICE'
}
$edgeFacing = @('frontend','gateway-api-service')
$cacheTtlWaitSec = 15   # Gate C — > TTL LB (5s) + fetch Eureka (5s)

function Wait-Healthy([string]$container, [int]$timeoutSec = 300) {
    Write-Host "  gate A: healthcheck de $container..." -NoNewline
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $deadline) {
        $s = docker inspect --format '{{.State.Health.Status}}' $container 2>$null
        if ($s -eq 'healthy') { Write-Host " healthy"; return }
        Start-Sleep 5
    }
    throw "TIMEOUT: $container nao ficou healthy em ${timeoutSec}s — rolling ABORTADO (a outra instancia continua a servir)."
}

function Wait-EurekaUp([string]$app, [int]$minUp, [int]$timeoutSec = 180) {
    if (-not $app) { return }
    Write-Host "  gate B: $app com >= $minUp instancias UP no Eureka..." -NoNewline
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $deadline) {
        try {
            $json = curl.exe -s -u "eureka:$($env:EUREKA_PASSWORD ?? 'eureka-secret-2024')" `
                -H 'Accept: application/json' "http://localhost:8761/eureka/apps/$app" | ConvertFrom-Json
            $up = @($json.application.instance | Where-Object status -eq 'UP').Count
            if ($up -ge $minUp) { Write-Host " $up UP"; return }
        } catch { }
        Start-Sleep 5
    }
    throw "TIMEOUT: $app nao atingiu $minUp instancias UP — rolling ABORTADO."
}

$app = $eurekaNames[$Service]

Write-Host "== Rolling deploy: $Service =="
Write-Host "-- 1/5 build da imagem nova (nada e recriado)"
docker compose @composeFiles build $Service
if ($LASTEXITCODE -ne 0) { throw "build falhou" }

Write-Host "-- 2/5 recriar instancia B ($Service-2); A continua a servir"
docker compose @composeFiles up -d --no-deps --no-build "$Service-2"
Wait-Healthy "$Service-2"
Wait-EurekaUp $app 2
Write-Host "  gate C: aguardar ${cacheTtlWaitSec}s (TTL caches LB/Eureka)"; Start-Sleep $cacheTtlWaitSec

Write-Host "-- 3/5 recriar instancia A ($Service); B continua a servir"
docker compose @composeFiles up -d --no-deps --no-build $Service
Wait-Healthy $Service
Wait-EurekaUp $app 2
Start-Sleep $cacheTtlWaitSec

if ($Service -in $edgeFacing) {
    Write-Host "-- 4/5 reload do nginx-edge (IPs novos dos upstreams)"
    docker exec nginx-edge nginx -s reload
    if ($LASTEXITCODE -ne 0) { throw "nginx reload falhou" }
} else {
    Write-Host "-- 4/5 (nao e servico de borda — reload dispensado)"
}

Write-Host "-- 5/5 DONE. Verifica o deploy-proof.log: 0 FAIL / 0 nao-2xx e a prova."
