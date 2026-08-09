# ════════════════════════════════════════════════════════════════════════
#  RED Ultimate — Gradle Cache Pre-warm
#  Downloads all Gradle dependencies BEFORE Docker build
#  This prevents DNS/network issues during Docker build
# ════════════════════════════════════════════════════════════════════════

param(
    [string]$ProjectRoot = "C:\Users\hpc01\Pictures\pro\RED_Ultimate_V1-main\RED_Ultimate"
)

$ErrorActionPreference = "Stop"
Set-Location $ProjectRoot

Write-Host "═══════════════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  RED Ultimate — Gradle Cache Pre-warm" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════════════════════════" -ForegroundColor Cyan

# Step 1: Check Java
Write-Host "`n[1/5] Checking Java..." -ForegroundColor Yellow
$java = & java -version 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "  ✗ Java not found. Please install Java 21." -ForegroundColor Red
    exit 1
}
Write-Host "  ✓ Java found: $($java[0])" -ForegroundColor Green

# Step 2: Check Gradle wrapper
Write-Host "[2/5] Checking Gradle wrapper..." -ForegroundColor Yellow
$gradlew = Join-Path $ProjectRoot "backend-server\gradlew"
if (-not (Test-Path $gradlew)) {
    Write-Host "  ✗ gradlew not found" -ForegroundColor Red
    exit 1
}
Write-Host "  ✓ gradlew found" -ForegroundColor Green

# Step 3: Pre-download dependencies
Write-Host "[3/5] Pre-downloading Gradle dependencies..." -ForegroundColor Yellow
Set-Location (Join-Path $ProjectRoot "backend-server")

# This will download all dependencies to ~/.gradle/caches
& .\gradlew dependencies --configuration runtimeClasspath 2>&1 | Out-Null

if ($LASTEXITCODE -ne 0) {
    Write-Host "  ⚠ Some downloads failed, retrying..." -ForegroundColor Yellow
    & .\gradlew dependencies --configuration compileClasspath 2>&1 | Out-Null
}
Write-Host "  ✓ Dependencies downloaded" -ForegroundColor Green

# Step 4: Pre-compile to verify
Write-Host "[4/5] Pre-compiling to verify..." -ForegroundColor Yellow
& .\gradlew compileKotlin --no-daemon 2>&1 | Out-Null
if ($LASTEXITCODE -eq 0) {
    Write-Host "  ✓ Compilation successful" -ForegroundColor Green
} else {
    Write-Host "  ✗ Compilation failed — check errors above" -ForegroundColor Red
    exit 1
}

# Step 5: Create cache volume for Docker
Write-Host "[5/5] Setting up Docker cache..." -ForegroundColor Yellow
Set-Location $ProjectRoot

# Create named volume for Gradle cache
docker volume create gradle-cache 2>&1 | Out-Null

# Copy local Gradle cache to Docker volume
$gradleCachePath = Join-Path $env:USERPROFILE ".gradle"
if (Test-Path $gradleCachePath) {
    Write-Host "  Copying Gradle cache to Docker volume..." -ForegroundColor Yellow
    docker run --rm -v "${gradleCachePath}:/source:ro" -v gradle-cache:/target alpine:3.20 sh -c "cp -r /source/. /target/" 2>&1 | Out-Null
    Write-Host "  ✓ Cache synced to Docker volume" -ForegroundColor Green
}

Write-Host "`n═══════════════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  ✓ Pre-warm complete! You can now run local-first-run.ps1" -ForegroundColor Green
Write-Host "═══════════════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "`nNext step:" -ForegroundColor Yellow
Write-Host "  .\scripts\local-first-run.ps1 -ServerIp 192.168.137.19" -ForegroundColor White
