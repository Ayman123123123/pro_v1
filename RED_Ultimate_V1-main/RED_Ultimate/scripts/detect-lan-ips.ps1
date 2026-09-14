<#
.SYNOPSIS
  Detect the host's LAN IPv4 addresses for dual-interface (Ethernet + Wi-Fi) support.

.DESCRIPTION
  RED runs its server in Docker and publishes every service port on 0.0.0.0, so the
  backend, Nginx, TURN and SFU are reachable on ALL host interfaces at once.
  What actually differs between interfaces is the IP we *advertise* to each peer:

    • CLIENT_LAN_IP  — the Wi-Fi IP the Android app / browser use to reach Nginx,
                       TURN and the SFU. Clients live on the wireless subnet.

  This script prints the values (Wi-Fi, Ethernet) so the
  local-first-run scripts can seed .env without guessing. Use -Json for machine use.

  Usage:
    powershell -ExecutionPolicy Bypass -File .\scripts\detect-lan-ips.ps1
    powershell -ExecutionPolicy Bypass -File .\scripts\detect-lan-ips.ps1 -Json
#>
param([switch]$Json)

$ErrorActionPreference = 'Stop'

function Get-AdapterIp {
    param(
        [Parameter(Mandatory = $true)][string]$AliasPattern,
        [switch]$IncludeDisconnected
    )
    $adapters = Get-NetAdapter -ErrorAction SilentlyContinue |
        Where-Object {
            ($_.Status -eq 'Up' -or $IncludeDisconnected) -and
            $_.InterfaceDescription -notmatch 'TAP|VPN|Virtual|Loopback|vEthernet|Hyper-V|Bluetooth|WAN' -and
            $_.InterfaceAlias -match $AliasPattern
        }
    foreach ($a in $adapters) {
        $ip = Get-NetIPAddress -InterfaceIndex $a.ifIndex -AddressFamily IPv4 -ErrorAction SilentlyContinue |
            Where-Object { $_.IPAddress -ne '127.0.0.1' -and $_.PrefixOrigin -ne 'Link' } |
            Select-Object -First 1 -ExpandProperty IPAddress
        if ($ip) { return $ip }
    }
    return $null
}

# Wi-Fi: any connected wireless adapter.
$wifiIp = Get-AdapterIp -AliasPattern 'Wi-?Fi|Wireless|WLAN'

# Ethernet: any connected wired adapter (Realtek/GbE/Intel Ethernet), excluding wireless.
$ethernetIp = Get-AdapterIp -AliasPattern 'Ethernet|GbE|Realtek|Intel.*Ethernet|10/100/1000' |
    Where-Object { $_ -ne $wifiIp }

# Fall back: if only one interface is up, use it as the client IP.
$clientLanIp = if ($wifiIp) { $wifiIp } elseif ($ethernetIp) { $ethernetIp } else { $null }

if ($Json) {
    [PSCustomObject]@{
        wifiIp       = $wifiIp
        ethernetIp   = $ethernetIp
        clientLanIp  = $clientLanIp
    } | ConvertTo-Json -Compress
} else {
    Write-Host "Wi-Fi IP        : $wifiIp"
    Write-Host "Ethernet IP     : $ethernetIp"
    Write-Host "Client LAN IP   : $clientLanIp   (used for Nginx/TURN/SFU advertisement)"
}
