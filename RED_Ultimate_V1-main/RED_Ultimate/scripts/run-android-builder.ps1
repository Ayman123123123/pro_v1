param(
    [string]$ServerUrl = 'http://192.168.1.50:8088',
    [ValidateSet('arm64-v8a', 'armeabi-v7a', 'x86_64')][string]$TargetAbi = 'arm64-v8a',
    [string]$Image = 'red-android-builder:latest',
    [string]$OutName = 'younes-app.apk'
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$OutPath = Join-Path $Root $OutName

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker Desktop is required' }
& docker info *> $null
if ($LASTEXITCODE -ne 0) { throw 'Docker Desktop is not running' }

Write-Host "Installing platform android-37 if missing, then assembleDebug..."
$cmd = @"
set -eu
export ANDROID_HOME=`${ANDROID_HOME:-/opt/android-sdk}
export ANDROID_SDK_ROOT=`$ANDROID_HOME
export PATH="`$ANDROID_HOME/cmdline-tools/latest/bin:`$ANDROID_HOME/platform-tools:`$PATH"
if command -v sdkmanager >/dev/null 2>&1; then
  yes | sdkmanager --licenses >/dev/null 2>&1 || true
  sdkmanager "platforms;android-37" "build-tools;36.0.0" || true
fi
cd /src
sed -i 's/\r`$//' gradlew || true
chmod +x gradlew
./gradlew :app:assembleDebug \
  -PRED_SERVER_URL='$ServerUrl' \
  -PRED_TARGET_ABI='$TargetAbi' \
  -PRED_SKIP_BUILD_LOGIC=true \
  --no-daemon --no-configuration-cache --stacktrace \
  -Dorg.gradle.jvmargs='-Xmx3g -Xms256m -XX:MaxMetaspaceSize=768m'
"@

& docker run --rm `
    -v "${Root}:/src" `
    -w /src `
    -e ANDROID_HOME=/opt/android-sdk `
    -e ANDROID_SDK_ROOT=/opt/android-sdk `
    $Image `
    bash -lc $cmd

if ($LASTEXITCODE -ne 0) { throw 'assembleDebug failed inside red-android-builder' }

$built = Get-ChildItem -Path (Join-Path $Root 'red-app\build\outputs\apk\debug') -Filter '*.apk' -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $built) { throw 'No APK under red-app/build/outputs/apk/debug' }
Copy-Item $built.FullName $OutPath -Force
$apk = Get-Item $OutPath
Write-Host "APK_READY path=$($apk.FullName)"
Write-Host "APK_SIZE_BYTES=$($apk.Length)"
Write-Host "APK_SHA256=$((Get-FileHash -Algorithm SHA256 $apk.FullName).Hash.ToLowerInvariant())"
