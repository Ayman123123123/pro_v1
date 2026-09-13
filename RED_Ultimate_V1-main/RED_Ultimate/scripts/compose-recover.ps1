<#
.SYNOPSIS
  Recover the YOUNES Docker stack after Docker Desktop crashes.

.DESCRIPTION
  Production truth is Docker (Kotlin + Postgres + Nginx on 8088).


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


if (-not (Test-Path $EnvFile)) {
    throw "Missing $EnvFile — copy .env.example to .env and set the passwords first."
}

Push-Location $Root
try {
    $composeArgs = @("--env-file", $EnvFile, "-f", "docker-compose.yml")

    docker compose @composeArgs config --quiet
    if ($LASTEXITCODE -ne 0) { throw "docker compose config failed" }

    if ($RebuildBackend) {
        Write-Host "Rebuilding backend image so the Postgres/Mongo binding fix is inside the container..." -ForegroundColor Yellow
        docker compose @composeArgs build backend
        if ($LASTEXITCODE -ne 0) { throw "backend image build failed" }
    }

    docker compose @composeArgs up -d
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
} finally {
    Pop-Location
}
