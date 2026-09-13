# Validate the canonical RED tree without changing Git state.
$ErrorActionPreference = "Stop"
$ProjectRoot = Join-Path $PSScriptRoot "..\RED_Ultimate_V1-main\RED_Ultimate"

if (-not (Test-Path $ProjectRoot)) { throw "Canonical RED project was not found: $ProjectRoot" }

Write-Host "Checking canonical project files..." -ForegroundColor Cyan
$files = @(
    "docker-compose.yml",
    "backend-server\build.gradle.kts",
    "red-app\build.gradle.kts",
    "admin_dashboard\package.json",
    "media-sfu\server.js"
)
foreach ($relative in $files) {
    $path = Join-Path $ProjectRoot $relative
    if (-not (Test-Path $path)) { throw "Missing required file: $relative" }
    Write-Host "PASS $relative" -ForegroundColor Green
}

Push-Location $ProjectRoot
try {
    docker compose --env-file .env.example config --quiet
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose validation failed" }
    Write-Host "PASS Docker Compose configuration" -ForegroundColor Green
} finally {
    Pop-Location
}
