Set-Location $PSScriptRoot
$env:_JAVA_OPTIONS="-Xmx3072m -XX:MaxMetaspaceSize=512m"
$env:GRADLE_OPTS="-Xmx3072m"
$ErrorActionPreference = "Continue"

Write-Host "=== Starting assembleDebug at $(Get-Date) ===" -ForegroundColor Green

& .\gradlew.bat :app:assembleDebug --no-daemon --no-configuration-cache 2>&1 | Tee-Object -FilePath build-assembly2.log

Write-Host "=== Build finished at $(Get-Date) ===" -ForegroundColor Green
