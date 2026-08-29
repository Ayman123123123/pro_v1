Set-Location "C:\Users\hpc01\Pictures\pro_new\RED_Ultimate_V1-main\RED_Ultimate"
$env:_JAVA_OPTIONS="-Xmx1024m -XX:MaxMetaspaceSize=512m"
$env:GRADLE_OPTS="-Xmx2048m"
$ErrorActionPreference = "Continue"

Write-Host "=== Starting assembleDebug at $(Get-Date) ===" -ForegroundColor Green

& .\gradlew.bat :app:assembleDebug --no-daemon --no-configuration-cache 2>&1 | Tee-Object -FilePath "build-assembly2.log"

Write-Host "=== Build finished at $(Get-Date) ===" -ForegroundColor Green
