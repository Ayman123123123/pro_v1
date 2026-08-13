param(
    [string]$ServerUrl = 'http://192.168.1.50:8088',
    [ValidateSet('arm64-v8a', 'armeabi-v7a', 'x86_64')][string]$TargetAbi = 'arm64-v8a',
    [string]$Image = 'red-android-builder:latest',
    [string]$OutName = 'younes-app.apk'
)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$OutPath = Join-Path $Root $OutName
$Container = 'red-apk-output'

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker Desktop is required' }
& docker info *> $null
if ($LASTEXITCODE -ne 0) { throw 'Docker Desktop is not running' }

$imageId = (& docker images -q $Image)
if (-not $imageId) { throw "Image $Image was not found. Build it first or pull it." }

function Copy-ApkFromContainer([string]$SourceContainer) {
    $candidates = @(
        '/build/RED_Ultimate/red-app/build/outputs/apk/debug/app-debug.apk',
        '/build/RED_Ultimate/red-app/build/outputs/apk/debug/red-app-debug.apk',
        '/opt/red-app-debug.apk',
        '/red-app-debug.apk'
    )
    foreach ($remote in $candidates) {
        & docker cp "${SourceContainer}:${remote}" $OutPath 2>$null
        if ($LASTEXITCODE -eq 0 -and (Test-Path $OutPath) -and ((Get-Item $OutPath).Length -gt 1MB)) {
            return $true
        }
        Remove-Item $OutPath -Force -ErrorAction SilentlyContinue
    }
    $listing = & docker exec $SourceContainer sh -c "ls -1 /build/RED_Ultimate/red-app/build/outputs/apk/debug/*.apk 2>/dev/null" 2>$null
    if ($listing) {
        $first = ($listing | Select-Object -First 1).Trim()
        if ($first) {
            & docker cp "${SourceContainer}:${first}" $OutPath
            if ($LASTEXITCODE -eq 0 -and (Test-Path $OutPath)) { return $true }
        }
    }
    return $false
}

Write-Host "Trying to extract an existing APK from $Image ..."
& docker rm -f $Container *> $null
& docker create --name $Container $Image *> $null
if ($LASTEXITCODE -ne 0) { throw "Could not create container from $Image" }

if (Copy-ApkFromContainer $Container) {
    & docker rm -f $Container *> $null
    $apk = Get-Item $OutPath
    Write-Host "APK_READY path=$($apk.FullName)"
    Write-Host "APK_SIZE_BYTES=$($apk.Length)"
    Write-Host "APK_SHA256=$((Get-FileHash -Algorithm SHA256 $apk.FullName).Hash.ToLowerInvariant())"
    return
}

Write-Host "No APK inside the image. Building with the existing SDK image (source mounted)..."
& docker rm -f $Container *> $null

$gradleCmd = @(
    'set -eu'
    'cd /src'
    "sed -i 's/\r$//' gradlew || true"
    'chmod +x gradlew'
    './gradlew :app:assembleDebug'
    "-PRED_SERVER_URL=`"$ServerUrl`""
    "-PRED_TARGET_ABI=`"$TargetAbi`""
    '-PRED_SKIP_BUILD_LOGIC=true'
    '--no-daemon --no-configuration-cache'
    '-Dorg.gradle.jvmargs="-Xmx3g -Xms256m -XX:MaxMetaspaceSize=768m"'
) -join ' '

& docker run --name $Container --rm `
    -v "${Root}:/src" `
    -w /src `
    -e ANDROID_HOME=/opt/android-sdk `
    $Image `
    sh -lc $gradleCmd

if ($LASTEXITCODE -ne 0) { throw 'Gradle assembleDebug failed inside red-android-builder' }

$built = Get-ChildItem -Path (Join-Path $Root 'red-app\build\outputs\apk\debug') -Filter '*.apk' -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $built) { throw 'Build finished but no APK was produced under red-app/build/outputs/apk/debug' }
Copy-Item $built.FullName $OutPath -Force
$apk = Get-Item $OutPath
Write-Host "APK_READY path=$($apk.FullName)"
Write-Host "APK_SIZE_BYTES=$($apk.Length)"
Write-Host "APK_SHA256=$((Get-FileHash -Algorithm SHA256 $apk.FullName).Hash.ToLowerInvariant())"
