param(
    [Parameter(Mandatory = $true)][ValidatePattern('^([0-9]{1,3}\.){3}[0-9]{1,3}$')][string]$ServerIp,
    [ValidateRange(1024, 65535)][int]$HttpPort = 8088,
    [ValidateSet('arm64-v8a', 'armeabi-v7a', 'x86_64')][string]$TargetAbi = 'arm64-v8a',
    [string]$TlsPins = ''
)
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$RepoRoot = Split-Path -Parent $Root
$Artifacts = Join-Path $RepoRoot 'local-artifacts'
$ServerUrl = 'http' + "://$ServerIp`:$HttpPort"

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Docker Desktop is required' }
& docker info *> $null
if ($LASTEXITCODE -ne 0) { throw 'Docker Desktop is not running' }

Write-Host "Preparing verified cryptographic dependencies..."
& (Join-Path $PSScriptRoot 'prefetch-android-crypto.ps1')
if (-not $?) { throw 'Verified dependency prefetch failed' }

New-Item -ItemType Directory -Force $Artifacts | Out-Null
$OldApk = Join-Path $Artifacts 'red-app-debug.apk'
Remove-Item $OldApk -Force -ErrorAction SilentlyContinue

Push-Location $RepoRoot
try {
    Write-Host "Building RED Android artifact for the configured LAN endpoint..."
    & docker build --file Dockerfile --target android-artifact `
        --build-arg "RED_SERVER_URL=$ServerUrl" `
        --build-arg "RED_TLS_PINS=$TlsPins" `
        --build-arg "RED_TARGET_ABI=$TargetAbi" `
        --output "type=local,dest=$Artifacts" .
    if ($LASTEXITCODE -ne 0) { throw 'Android artifact build failed' }
} finally { Pop-Location }

if (-not (Test-Path $OldApk)) { throw 'Build completed without red-app-debug.apk' }
$apk = Get-Item $OldApk
$hash = (Get-FileHash -Algorithm SHA256 $OldApk).Hash.ToLowerInvariant()
Write-Host "APK_READY path=$($apk.FullName)"
Write-Host "APK_SIZE_BYTES=$($apk.Length)"
Write-Host "APK_SHA256=$hash"
