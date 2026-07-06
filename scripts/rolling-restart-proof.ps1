# ==============================================================
# Rolling RESTART (bounce, no rebuild) of ONE HA service with gates.
# Use when config/image is unchanged and you need a zero-downtime bounce
# (docker restart — NEVER --force-recreate; see ha-zero-downtime-deploy skill).
# Restarts instance B (<svc>-2) then A (<svc>), gating each step on:
#   Gate A: container healthcheck = healthy
#   Gate B: Eureka shows 2 instances UP for the app
#   Gate C: 20s wait (> LB/Eureka cache TTL with scale-overlay tuning)
# All steps are logged as === markers in deploy-proof-services.log so the
# proof-loop analysis can locate the restart windows.
#
# Usage: .\scripts\rolling-restart-proof.ps1 -Svc product-service -App PRODUCT-SERVICE
# ==============================================================
param(
    [Parameter(Mandatory)][string]$Svc,
    [Parameter(Mandatory)][string]$App
)
$ErrorActionPreference = 'Stop'
$log = Join-Path $PSScriptRoot '..\deploy-proof-services.log'
$cred = New-Object PSCredential('eureka', (ConvertTo-SecureString 'eureka-secret-2024' -AsPlainText -Force))

function Mark($m) { "$(Get-Date -f HH:mm:ss.fff) === $m ===" | Add-Content $log; Write-Output $m }

function Wait-Healthy($container) {
    $deadline = (Get-Date).AddSeconds(240)
    while ((Get-Date) -lt $deadline) {
        $h = docker inspect --format '{{.State.Health.Status}}' $container 2>$null
        if ($h -eq 'healthy') { return $true }
        Start-Sleep -Seconds 5
    }
    return $false
}

function Wait-Eureka2Up($app) {
    $deadline = (Get-Date).AddSeconds(180)
    while ((Get-Date) -lt $deadline) {
        try {
            $r = Invoke-RestMethod -Uri "http://localhost:8761/eureka/apps/$app" -Headers @{ Accept = 'application/json' } -Credential $cred -AllowUnencryptedAuthentication -TimeoutSec 10
            $up = @($r.application.instance) | Where-Object { $_.status -eq 'UP' }
            if (@($up).Count -ge 2) { return $true }
        } catch { }
        Start-Sleep -Seconds 5
    }
    return $false
}

function Roll-Instance($container, $app) {
    Mark "RESTART $container"
    docker restart $container | Out-Null
    if (-not (Wait-Healthy $container)) { Mark "GATE-A FAILED: $container not healthy in 240s"; exit 1 }
    Mark "GATE-A OK: $container healthy"
    if (-not (Wait-Eureka2Up $app)) { Mark "GATE-B FAILED: $app <2 UP in Eureka after 180s"; exit 1 }
    Mark "GATE-B OK: $app 2x UP in Eureka"
    Start-Sleep -Seconds 20
    Mark "GATE-C OK: TTL window elapsed"
}

Mark "ROLLING START $Svc"
Roll-Instance "$Svc-2" $App
Roll-Instance $Svc $App
Mark "ROLLING DONE $Svc"
