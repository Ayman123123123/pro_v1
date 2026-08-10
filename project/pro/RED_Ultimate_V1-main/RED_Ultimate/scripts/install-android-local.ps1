param(
    [string]$ApkPath = "",
    [switch]$ReplaceIncompatible
)
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$RepoRoot = Split-Path -Parent $Root
if (-not $ApkPath) { $ApkPath = Join-Path $RepoRoot 'local-artifacts\red-app-debug.apk' }
if (-not (Test-Path $ApkPath)) {
    throw "APK not found: $ApkPath. Build it first with scripts\build-android-local.ps1 -ServerIp <LAN-IP>."
}

$ToolsRoot = Join-Path $RepoRoot 'local-tools'
$Adb = Join-Path $ToolsRoot 'platform-tools\adb.exe'
if (-not (Test-Path $Adb)) {
    New-Item -ItemType Directory -Force $ToolsRoot | Out-Null
    $zip = Join-Path $ToolsRoot 'platform-tools-windows.zip'
    $url = 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip'
    Write-Host 'Downloading official Google Android Platform-Tools...'
    & curl.exe --fail --location --retry 12 --retry-all-errors --retry-delay 5 --continue-at - --output $zip $url
    if ($LASTEXITCODE -ne 0) { throw 'Platform-Tools download failed' }
    if (Test-Path (Join-Path $ToolsRoot 'platform-tools')) { Remove-Item (Join-Path $ToolsRoot 'platform-tools') -Recurse -Force }
    Expand-Archive -Path $zip -DestinationPath $ToolsRoot -Force
    if (-not (Test-Path $Adb)) { throw 'Official Platform-Tools archive did not contain adb.exe' }
}

& $Adb start-server | Out-Null
Write-Host 'Connect the phone, enable Developer options + USB debugging, and accept the RSA prompt.'
$devices = & $Adb devices
$authorized = @($devices | Where-Object { $_ -match "\tdevice$" })
if ($authorized.Count -eq 0) {
    Write-Host ($devices -join "`n")
    throw 'No authorized Android device. Reconnect USB and accept the debugging prompt, then rerun.'
}
if ($authorized.Count -gt 1) { throw 'More than one Android device is connected; disconnect extras for this Alpha test.' }

Write-Host "Installing verified YOUNES debug APK ($((Get-Item $ApkPath).Length) bytes)..."
# Windows PowerShell can promote native stderr to a terminating ErrorRecord when the script uses
# ErrorActionPreference=Stop. Capture ADB's expected signature diagnostic without aborting our handler.
$previousErrorPreference = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$installOutput = @(& $Adb install -r -t $ApkPath 2>&1)
$installExit = $LASTEXITCODE
$ErrorActionPreference = $previousErrorPreference
$installText = ($installOutput | ForEach-Object { $_.ToString() }) -join "`n"
Write-Host $installText
if ($installExit -ne 0 -and $installText -match 'INSTALL_FAILED_UPDATE_INCOMPATIBLE') {
    if (-not $ReplaceIncompatible) {
        throw 'An older YOUNES/RED Alpha uses another debug signature. Rerun with -ReplaceIncompatible to uninstall it (this deletes that app local data) and install the stable-signed Alpha.'
    }
    Write-Warning 'Removing incompatible Alpha package and its local data once.'
    & $Adb uninstall com.red.sovereign
    if ($LASTEXITCODE -ne 0) { throw 'Unable to remove incompatible YOUNES package' }
    & $Adb install -t $ApkPath
    if ($LASTEXITCODE -ne 0) { throw 'ADB installation failed after removing incompatible package' }
} elseif ($installExit -ne 0) {
    throw 'ADB installation failed'
}
$package = (& $Adb shell pm list packages com.red.sovereign)
if ($package -notmatch 'package:com.red.sovereign') { throw 'YOUNES package was not found after installation' }
Write-Host 'YOUNES_INSTALL_PASS package=com.red.sovereign'
Write-Host 'Launch YOUNES on the phone, then use Discover and verify local server before registration.'
