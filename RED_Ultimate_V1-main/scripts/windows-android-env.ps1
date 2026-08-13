#Requires -Version 5.1
<#
.SYNOPSIS
  Unify Android Gradle prefs on Windows (Gradle 9.4+ / AGP Isolated Projects).

.DESCRIPTION
  AndroidLocationsBuildService fails when ANDROID_PREFS_ROOT and
  ANDROID_USER_HOME point at different folders (Docker .android_home vs
  host .android). This script sets both User-level variables to
  %USERPROFILE%\.android and aligns the current session.

  Run once in an elevated or normal PowerShell, then restart Android Studio.
#>
$ErrorActionPreference = "Stop"
$androidPrefs = Join-Path $env:USERPROFILE ".android"
New-Item -ItemType Directory -Force -Path $androidPrefs | Out-Null

function Set-UserEnv([string]$Name, [string]$Value) {
    [Environment]::SetEnvironmentVariable($Name, $Value, "User")
    Set-Item -Path "Env:$Name" -Value $Value
}

Set-UserEnv "ANDROID_USER_HOME" $androidPrefs
Set-UserEnv "ANDROID_PREFS_ROOT" $androidPrefs

$sdkHome = [Environment]::GetEnvironmentVariable("ANDROID_SDK_HOME", "User")
$androidHome = [Environment]::GetEnvironmentVariable("ANDROID_HOME", "User")
if ($sdkHome -and $androidHome -and $sdkHome -eq $androidHome) {
    Write-Host "ANDROID_SDK_HOME equals ANDROID_HOME (deprecated). Clearing user ANDROID_SDK_HOME."
    [Environment]::SetEnvironmentVariable("ANDROID_SDK_HOME", $null, "User")
    Remove-Item Env:ANDROID_SDK_HOME -ErrorAction SilentlyContinue
}

Write-Host "Android prefs unified to $androidPrefs"
Write-Host "Restart Android Studio / the Gradle daemon (gradlew --stop) before building."
