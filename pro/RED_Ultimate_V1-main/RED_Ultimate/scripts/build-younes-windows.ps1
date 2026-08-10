<#
Builds the official YOUNES Android app (:app → red-app) on Windows.

Prerequisites:
  - JDK 21
  - Android SDK platform 37, Build Tools 36.0.0 and NDK 28.0.13004108
  - An internet connection for Gradle dependencies on the first run

This script writes local.properties only when it is missing. local.properties is ignored by Git.
#>
[CmdletBinding()]
param(
    [switch]$SkipUnitTests
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $projectRoot

function Fail([string]$Message) {
    Write-Error $Message
    exit 1
}

if (-not (Test-Path '.\gradlew.bat')) {
    Fail "gradlew.bat was not found. Run this script from the unified YOUNES repository."
}

$java = Get-Command java -ErrorAction SilentlyContinue
if ($null -eq $java) {
    Fail "JDK 21 is required. Install Eclipse Temurin 21 JDK, set JAVA_HOME, reopen PowerShell, then retry."
}

$javaVersion = (& java -version 2>&1 | Out-String)
if ($javaVersion -notmatch 'version "21\.') {
    Fail "JDK 21 is required. Current Java output:`n$javaVersion"
}

$sdkCandidates = @(
    $env:ANDROID_SDK_ROOT,
    $env:ANDROID_HOME,
    (Join-Path $env:LOCALAPPDATA 'Android\Sdk')
) | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique

$sdkRoot = $sdkCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if ($null -eq $sdkRoot) {
    Fail "Android SDK was not found. Install Android Studio and SDK components, then set ANDROID_SDK_ROOT."
}

$requiredPaths = @(
    (Join-Path $sdkRoot 'platforms\android-37'),
    (Join-Path $sdkRoot 'build-tools\36.0.0'),
    (Join-Path $sdkRoot 'ndk\28.0.13004108')
)
$missing = $requiredPaths | Where-Object { -not (Test-Path $_) }
if ($missing.Count -gt 0) {
    Fail "Android SDK components are missing:`n$($missing -join "`n")`nInstall them in Android Studio SDK Manager, then retry."
}

if (-not (Test-Path '.\local.properties')) {
    $escapedSdk = $sdkRoot.Replace('\', '\\')
    Set-Content -Path '.\local.properties' -Value "sdk.dir=$escapedSdk" -NoNewline
    Write-Host "Created ignored local.properties for this workstation." -ForegroundColor DarkGray
}

$env:JAVA_HOME = (Split-Path (Split-Path $java.Source -Parent) -Parent)
Write-Host "Building official YOUNES app from $projectRoot" -ForegroundColor Cyan
Write-Host "Java: $javaVersion" -ForegroundColor DarkGray
Write-Host "Android SDK: $sdkRoot" -ForegroundColor DarkGray

& .\gradlew.bat :app:assembleDebug --no-daemon --stacktrace
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

if (-not $SkipUnitTests) {
    & .\gradlew.bat :app:testDebugUnitTest --no-daemon --stacktrace
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

$apkDirectory = Join-Path $projectRoot 'red-app\build\outputs\apk\debug'
$apk = Get-ChildItem -Path $apkDirectory -Filter '*.apk' -ErrorAction SilentlyContinue | Select-Object -First 1
if ($null -eq $apk) {
    Fail "Gradle completed but no debug APK was found under $apkDirectory"
}

Write-Host "SUCCESS: $($apk.FullName)" -ForegroundColor Green
