<#
.SYNOPSIS
  Diagnose RED Docker stack on Windows. Does NOT pipe V1.sql into Postgres.

.DESCRIPTION
  Flyway inside red-backend owns V1..V29. Hand-applying SQL files breaks
  flyway_schema_history and is the usual cause of an empty "flyway" grep.
  If docker build hangs on apt/ffmpeg, Ctrl+C, git pull, then rebuild:
    docker compose build --no-cache backend
    docker compose up -d backend

  Usage (from RED_Ultimate):
    powershell -ExecutionPolicy Bypass -File .\scripts\diagnose-stack.ps1
    powershell -ExecutionPolicy Bypass -File .\scripts\diagnose-stack.ps1 -RestartBackend
    powershell -ExecutionPolicy Bypass -File .\scripts\compose-recover.ps1 -RebuildBackend
#>
param(
    [switch]$RestartBackend
)

$ErrorActionPreference = "Continue"
$Root = Split-Path -Parent $PSScriptRoot
$EnvFile = Join-Path $Root ".env"

Write-Host "=== YOUNES stack diagnose ===" -ForegroundColor Cyan
Write-Host "Root: $Root"

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker is not on PATH. Start Docker Desktop first."
}
docker info *> $null
if ($LASTEXITCODE -ne 0) { throw "Docker Desktop is not running." }

Write-Host "`n--- containers ---" -ForegroundColor Yellow
docker ps -a --filter "name=red-" --format "table {{.Names}}`t{{.Status}}`t{{.Ports}}"

if ($RestartBackend) {
    if (-not (Test-Path $EnvFile)) { throw "Missing $EnvFile — copy .env.example to .env first." }
    Write-Host "`n--- rebuilding backend so Flyway can run ---" -ForegroundColor Yellow
    Push-Location $Root
    try {
        docker compose --env-file $EnvFile up -d --build backend
    } finally { Pop-Location }
    Start-Sleep -Seconds 5
}

Write-Host "`n--- red-backend inspect ---" -ForegroundColor Yellow
docker inspect red-backend --format "Status={{.State.Status}} Exit={{.State.ExitCode}} OOM={{.State.OOMKilled}} Health={{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}" 2>$null
if ($LASTEXITCODE -ne 0) {
    Write-Host "red-backend does not exist. From RED_Ultimate run:" -ForegroundColor Red
    Write-Host "  .\\scripts\\local-first-run.ps1 -ServerIp <your-LAN-IPv4>"
    exit 1
}

Write-Host "`n--- last 120 backend log lines ---" -ForegroundColor Yellow
docker logs --tail 120 red-backend 2>&1

Write-Host "`n--- FLYWAY / ERROR hits ---" -ForegroundColor Yellow
$hits = docker logs red-backend 2>&1 | Select-String -Pattern "FLYWAY|Flyway|flywaydb|ERROR|Exception|Started Red"
if ($hits) { $hits | ForEach-Object { $_.Line } } else { Write-Host "(none) — JVM probably never reached Spring Boot" -ForegroundColor Red }

Write-Host "`n--- flyway_schema_history (if Postgres is up) ---" -ForegroundColor Yellow
docker exec red-db-sql psql -U admin -d red_sovereign -c "SELECT installed_rank, version, success, description, installed_on FROM flyway_schema_history ORDER BY installed_rank;" 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "Could not read flyway_schema_history. Postgres may be empty or backend never migrated." -ForegroundColor Red
}

Write-Host "`n--- health ---" -ForegroundColor Yellow
try {
    $h = Invoke-WebRequest -Uri "http://127.0.0.1:8088/health" -UseBasicParsing -TimeoutSec 5
    Write-Host "HTTP $($h.StatusCode)"
    Write-Host $h.Content
} catch {
    Write-Host "http://127.0.0.1:8088/health failed: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Trying in-container /health ..."
    docker exec red-backend curl -fsS http://127.0.0.1:8080/health 2>&1
}

Write-Host "`nDo NOT run: psql < V1__Initial_Schema.sql" -ForegroundColor Yellow
Write-Host "If history is empty, restart backend and let Flyway apply V1..V28:" -ForegroundColor Yellow
Write-Host "  powershell -ExecutionPolicy Bypass -File .\\scripts\\diagnose-stack.ps1 -RestartBackend"
