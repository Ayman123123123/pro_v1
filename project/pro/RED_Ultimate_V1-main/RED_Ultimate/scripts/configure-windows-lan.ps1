param(
    [ValidateRange(1024, 65535)][int]$HttpPort = 8088,
    [switch]$TrustCurrentNetwork,
    [switch]$EnableMediaPorts,
    [switch]$EnableDinstarPorts
)
$ErrorActionPreference = 'Stop'
$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    $arguments = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', "`"$PSCommandPath`"", '-HttpPort', $HttpPort)
    if ($TrustCurrentNetwork) { $arguments += '-TrustCurrentNetwork' }
    if ($EnableMediaPorts) { $arguments += '-EnableMediaPorts' }
    if ($EnableDinstarPorts) { $arguments += '-EnableDinstarPorts' }
    Start-Process powershell.exe -Verb RunAs -ArgumentList $arguments
    Write-Host 'Requested administrator permission in a new PowerShell window.'
    exit 0
}

$profiles = @(Get-NetConnectionProfile | Where-Object { $_.IPv4Connectivity -ne 'Disconnected' })
$public = @($profiles | Where-Object NetworkCategory -eq 'Public')
if ($public.Count -gt 0 -and -not $TrustCurrentNetwork) {
    throw "The active network is Public. Rerun with -TrustCurrentNetwork only if this is your trusted private LAN/hotspot."
}
if ($TrustCurrentNetwork) {
    $public | ForEach-Object { Set-NetConnectionProfile -InterfaceIndex $_.InterfaceIndex -NetworkCategory Private }
}

function Ensure-Rule([string]$Name, [string]$Protocol, [string]$Ports, [string]$RemoteAddress = 'Any') {
    Get-NetFirewallRule -DisplayName $Name -ErrorAction SilentlyContinue | Remove-NetFirewallRule
    New-NetFirewallRule -DisplayName $Name -Direction Inbound -Protocol $Protocol -LocalPort $Ports -RemoteAddress $RemoteAddress -Action Allow -Profile Private | Out-Null
    Write-Host "FIREWALL_RULE_READY $Name $Protocol/$Ports remote=$RemoteAddress Private"
}
@('RED Local HTTPS-Alpha','RED TURN','RED SFU RTP','RED TURN Relay') | ForEach-Object {
    Get-NetFirewallRule -DisplayName $_ -ErrorAction SilentlyContinue | Remove-NetFirewallRule
}
Ensure-Rule 'YOUNES Local HTTP Alpha' 'TCP' "$HttpPort"
if ($EnableMediaPorts) {
    Ensure-Rule 'YOUNES TURN' 'UDP' '3478'
    Ensure-Rule 'YOUNES SFU RTP' 'UDP' '40000-40100'
    Ensure-Rule 'YOUNES TURN Relay' 'UDP' '45000-45050'
}
if ($EnableDinstarPorts) {
    Ensure-Rule 'YOUNES DINSTAR SIP' 'UDP' '5060' '192.168.11.1'
    Ensure-Rule 'YOUNES DINSTAR RTP' 'UDP' '10000-10100' '192.168.11.1'
}
Write-Host 'YOUNES_WINDOWS_LAN_READY'
