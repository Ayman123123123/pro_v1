<#
.SYNOPSIS
  Bring the proven UC2000 (192.168.11.2 on the dedicated NIC) online for YOUNES.

.DESCRIPTION
  Keep the factory management IP. The box has no Wi-Fi: unplugging the
  Realtek cable always disconnects it, so moving it to 192.168.0.x does
  not help. Internet stays on Wi-Fi; DINSTAR stays on the management NIC.

  This script:
    1. Pings 192.168.11.2 and finds the Windows address on that NIC.
    2. Sets DINSTAR_ENABLED=true in .env (does not change IP/passwords).
    3. Enables IP forwarding so Docker Desktop / WSL can reach the NIC.
    4. Opens the SIP/RTP firewall rules toward the box.
    5. Starts Compose with docker-compose.lan.yml.
    6. Prints the SIP-server IP you must type in enFrame.htm.

  Usage (from RED_Ultimate, elevated PowerShell recommended):
    powershell -ExecutionPolicy Bypass -File .\scripts\enable-dinstar-ready.ps1
    powershell -ExecutionPolicy Bypass -File .\scripts\enable-dinstar-ready.ps1 -EnableMirroredNetworking
#>
param(
    [string]$DinstarIp = "192.168.11.2",
    [switch]$EnableMirroredNetworking
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$EnvFile = Join-Path $Root ".env"

Write-Host "=== YOUNES DINSTAR ready ===" -ForegroundColor Cyan
Write-Host "Keep $DinstarIp on the dedicated NIC. Do not move the box onto Wi-Fi."

if (-not (Test-Path $EnvFile)) {
    throw "Missing $EnvFile — copy .env.example to .env first."
}

$ping = Test-Connection -ComputerName $DinstarIp -Count 2 -Quiet -ErrorAction SilentlyContinue
if (-not $ping) {
    throw "$DinstarIp does not answer ping. Check the Realtek cable and that the UC2000 is powered."
}
Write-Host "PING $DinstarIp  PASS" -ForegroundColor Green

$mgmt = Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object { $_.IPAddress -like '192.168.11.*' -and $_.IPAddress -ne $DinstarIp } |
    Select-Object -First 1
if (-not $mgmt) {
    throw "This PC has no 192.168.11.x address. Assign a static IP such as 192.168.11.2/24 on the Realtek NIC."
}
Write-Host "Windows management IP: $($mgmt.IPAddress)  (adapter $($mgmt.InterfaceAlias))"

try {
    Set-NetIPInterface -InterfaceIndex $mgmt.InterfaceIndex -Forwarding Enabled -ErrorAction Stop
    Get-NetIPInterface -AddressFamily IPv4 |
        Where-Object { $_.InterfaceAlias -match 'WSL|vEthernet|Docker' } |
        ForEach-Object {
            Set-NetIPInterface -InterfaceIndex $_.InterfaceIndex -Forwarding Enabled -ErrorAction SilentlyContinue
        }
    Write-Host "IP forwarding enabled on the management NIC and WSL/Docker adapters."
} catch {
    Write-Host "Could not enable IP forwarding (run PowerShell as Administrator): $($_.Exception.Message)" -ForegroundColor Yellow
}

$envText = Get-Content $EnvFile -Raw
if ($envText -match '(?m)^DINSTAR_ENABLED=') {
    $envText = [regex]::Replace($envText, '(?m)^DINSTAR_ENABLED=.*$', 'DINSTAR_ENABLED=true')
} else {
    $envText = $envText.TrimEnd() + "`r`nDINSTAR_ENABLED=true`r`n"
}
if ($envText -notmatch '(?m)^DINSTAR_IP=') {
    $envText = $envText.TrimEnd() + "`r`nDINSTAR_IP=$DinstarIp`r`n"
}
[IO.File]::WriteAllText($EnvFile, $envText, [Text.UTF8Encoding]::new($false))
Write-Host "DINSTAR_ENABLED=true written to .env (IP/passwords left untouched)."

if ($EnableMirroredNetworking) {
    $wslConfig = Join-Path $env:USERPROFILE ".wslconfig"
    $block = @"
[wsl2]
networkingMode=mirrored
"@
    if (-not (Test-Path $wslConfig)) {
        Set-Content -Path $wslConfig -Value $block -Encoding ASCII
        Write-Host "Created $wslConfig with mirrored networking. Restart Docker Desktop."
    } elseif ((Get-Content $wslConfig -Raw) -notmatch 'networkingMode\s*=\s*mirrored') {
        Add-Content -Path $wslConfig -Value "`r`n$block"
        Write-Host "Appended mirrored networking to $wslConfig. Restart Docker Desktop."
    } else {
        Write-Host "mirrored networking already present in $wslConfig"
    }
}

$fw = Join-Path $PSScriptRoot "configure-windows-lan.ps1"
if (Test-Path $fw) {
    Write-Host "Opening SIP/RTP firewall toward $DinstarIp ..."
    & $fw -EnableDinstarPorts
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw "Docker is not on PATH."
}
docker info *> $null
if ($LASTEXITCODE -ne 0) {
    throw "Docker Desktop is not running. Start it (green whale) and re-run."
}

Push-Location $Root
try {
    docker compose --env-file $EnvFile -f docker-compose.yml -f docker-compose.lan.yml up -d backend pstn-gateway nginx
    if ($LASTEXITCODE -ne 0) { throw "compose up failed" }
} finally {
    Pop-Location
}

Write-Host ""
Write-Host "On the UC2000 web UI (https://${DinstarIp}/enLogin.htm → SIP / SIP Server):" -ForegroundColor Yellow
Write-Host "  SIP Server IP   = $($mgmt.IPAddress)"
Write-Host "  SIP Server Port = 5060"
Write-Host "  Transport       = UDP"
Write-Host "Asterisk identifies the box by IP (type=identify). DINSTAR_SIP_PASSWORD is reserved until an authenticated trunk is enabled."
Write-Host ""
Write-Host "Next: http://127.0.0.1:8088/  → DINSTAR fleet. /health must show bindings.mongodbHost=db-mongo"
Write-Host "Do not run a host JVM against localhost:27017 while Compose owns Mongo."
