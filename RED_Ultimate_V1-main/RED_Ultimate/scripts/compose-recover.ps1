<#
.SYNOPSIS
  Recover the YOUNES Docker stack after Docker Desktop crashes or a port fight.

.DESCRIPTION
  Production truth is Docker (Kotlin + Postgres + Nginx on 8088).
  The Node + SQLite mock (`npm run dev:server`) binds host :8080 and must
  NOT run at the same time. Backend 8080 exists only inside the compose
  network; browsers talk to http://127.0.0.1:8088.

  Usage (from RED_Ultimate):
    powershell -ExecutionPolicy Bypass -File .\scripts\compose-recover.ps1
    powershell -ExecutionPolicy Bypass -File .\scripts\compose-recover.ps1 -RebuildBackend
#>
param(
    [switch]$RebuildBackend
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$EnvFile = Join-Path $Root ".env"

Write-Host "=== YOUNES Docker recover ===" -ForegroundColor Cyan
Write-Host "Root: $Root"

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker is not on PATH. Install / start Docker Desktop and wait until the whale is green."
}

docker info *> $null
if ($LASTEXITCODE -ne 0) {
    throw @"
Docker Desktop is not responding (npipe:////./pipe/dockerDesktopLinuxEngine).
1. Quit Docker Desktop from the tray.
2. Start it again and wait until the whale is green / Engine running.
3. Re-run this script.
"@
}

function Stop-HostPort([int]$Port) {
    $conns = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if (-not $conns) { return }
    foreach ($procId in ($conns.OwningProcess | Select-Object -Unique)) {
        if (-not $procId -or $procId -eq 0) { continue }
        $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
        $name = if ($proc) { $proc.ProcessName } else { "pid $procId" }
        Write-Host "Stopping host listener on :$Port ($name / $procId) so it cannot shadow Docker." -ForegroundColor Yellow
        Stop-Process -Id $procId -Force -ErrorAction SilentlyContinue
    }
}

# Host :8080 is the Node/SQLite mock. Host :8088 is Nginx. Never let the mock
# answer /api/admin/users while the real stack is supposed to be in charge.
Stop-HostPort 8080

if (-not (Test-Path $EnvFile)) {
    throw "Missing $EnvFile — copy .env.example to .env and set the passwords first."
}

Push-Location $Root
try {
    docker compose --env-file $EnvFile config --quiet
    if ($LASTEXITCODE -ne 0) { throw "docker compose config failed" }

    if ($RebuildBackend) {
        Write-Host "Rebuilding backend image so the Postgres search fix is inside the container..." -ForegroundColor Yellow
        docker compose --env-file $EnvFile build backend
        if ($LASTEXITCODE -ne 0) { throw "backend image build failed" }
    }

    docker compose --env-file $EnvFile up -d
    if ($LASTEXITCODE -ne 0) { throw "docker compose up failed" }

    Write-Host -NoNewline "Waiting for http://127.0.0.1:8088/health"
    $healthy = $false
    foreach ($attempt in 1..60) {
        try {
            $response = Invoke-WebRequest -Uri "http://127.0.0.1:8088/health" -UseBasicParsing -TimeoutSec 3
            if ($response.StatusCode -eq 200) { $healthy = $true; break }
        } catch { }
        Write-Host -NoNewline "."
        Start-Sleep -Seconds 3
    }
    Write-Host ""
    if (-not $healthy) {
        docker compose --env-file $EnvFile ps
        docker compose --env-file $EnvFile logs --tail=120 backend
        throw "Nginx /health on 8088 did not become ready. Open Docker Desktop and retry."
    }
    Write-Host "PASS  http://127.0.0.1:8088/health" -ForegroundColor Green
    Write-Host "Admin panel: http://127.0.0.1:8088/"
    Write-Host "Do not run npm run dev:server while this stack is up."
} finally {
    Pop-Location
}
