# ══════════════════════════════════════════════════════════════════════
# 📱 YOUNES APK Builder - Windows PowerShell Script
# This script bypasses all Windows/Gradle path issues by building inside Docker.
# ══════════════════════════════════════════════════════════════════════

param (
    [string]$ServerUrl = "http://192.168.0.181:8088"
)

Write-Host "🚀 Starting YOUNES Legendary APK Build..." -ForegroundColor Cyan

# 1. Clean previous attempts
docker rm -f red-apk-final 2>$null
Remove-Item .\younes-app.apk -ErrorAction SilentlyContinue

# 2. Build the Docker image (if not updated)
Write-Host "📦 Preparing build environment (Docker)..." -ForegroundColor Yellow
docker build -t red-android-builder -f android-build.Dockerfile .

# 3. Run the build container
Write-Host "🏗️ Compiling APK inside container (this may take 5-10 mins)..." -ForegroundColor Yellow
Write-Host "🔗 Target Server: $ServerUrl" -ForegroundColor Gray

docker run --name red-apk-final `
  --entrypoint /bin/bash `
  red-android-builder `
  -c "apt-get update && apt-get install -y dos2unix && dos2unix gradlew && chmod +x gradlew && ./gradlew :app:assembleDebug -PRED_SERVER_URL=$ServerUrl -Porg.gradle.dependency.verification=off --no-daemon"

# 4. Extract the APK
if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ Build Success! Extracting APK..." -ForegroundColor Green
    docker cp red-apk-final:/build/red-app/build/outputs/apk/debug/red-app-debug.apk ./younes-app.apk
    Write-Host "✨ Done! Your APK is ready at: $(Get-Location)\younes-app.apk" -ForegroundColor Cyan
} else {
    Write-Host "❌ Build Failed inside Docker. Check logs above." -ForegroundColor Red
}

# 5. Cleanup
docker rm red-apk-final 2>$null
