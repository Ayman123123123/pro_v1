Set-Location "C:\Users\hpc01\Pictures\pro_new\RED_Ultimate_V1-main\RED_Ultimate"
$env:_JAVA_OPTIONS="-Xmx512m"
$env:GRADLE_OPTS="-Xmx1024m"
$ErrorActionPreference = "Continue"

Write-Host "=== Starting build at $(Get-Date) ===" -ForegroundColor Green

# Build Android KSP + compileKotlin
Write-Host "--- Phase 1: KSP + Kotlin Compile ---" -ForegroundColor Yellow
& .\gradlew.bat :app:kspDebugKotlin --no-daemon --no-configuration-cache 2>&1 | Tee-Object -FilePath "build-ksp.log"

Write-Host "--- Phase 2: Full Debug Assembly ---" -ForegroundColor Yellow
& .\gradlew.bat :app:assembleDebug --no-daemon --no-configuration-cache 2>&1 | Tee-Object -FilePath "build-assembly.log"

Write-Host "=== Build finished at $(Get-Date) ===" -ForegroundColor Green
