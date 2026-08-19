<#
.SYNOPSIS
  Read-only preflight for the RED ↔ DINSTAR UC2000-VE LAN path.

.DESCRIPTION
  Verifies that the Windows host has a management-NIC address in the same
  subnet as the UC2000-VE, that the gateway web port is reachable, that
  Asterisk/Docker and UDP 5060 are present, and that the restrictive firewall
  rules for the gateway are installed. It deliberately does not edit network
  configuration, restart containers, send SMS, originate a call, or change
  any DINSTAR setting.
#>
param(
    [string]$DinstarIp = '192.168.11.2',
    [string]$ExpectedAsteriskIp = '',
    [ValidateRange(1, 65535)][int]$WebPort = 443,
    [ValidateRange(1, 65535)][int]$SipPort = 5060
)

$ErrorActionPreference = 'Stop'

function Result([string]$Name, [string]$Status, [string]$Detail) {
    [pscustomobject]@{ check = $Name; status = $Status; detail = $Detail }
}

$results = [System.Collections.Generic.List[object]]::new()
$managementAddresses = @(
    Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
    Where-Object { $_.IPAddress -like '192.168.11.*' -and $_.IPAddress -ne $DinstarIp }
)

if ($managementAddresses.Count -eq 0) {
    $results.Add((Result 'management-nic' 'FAIL' "No host address in 192.168.11.0/24 besides $DinstarIp"))
} else {
    $addresses = ($managementAddresses | ForEach-Object { "$($_.IPAddress) on $($_.InterfaceAlias)" }) -join '; '
    $status = if ($ExpectedAsteriskIp -and -not ($managementAddresses.IPAddress -contains $ExpectedAsteriskIp)) { 'FAIL' } else { 'PASS' }
    $detail = if ($status -eq 'FAIL') { "Expected Asterisk IP $ExpectedAsteriskIp is not assigned; found $addresses" } else { $addresses }
    $results.Add((Result 'management-nic' $status $detail))
}

$web = Test-NetConnection -ComputerName $DinstarIp -Port $WebPort -WarningAction SilentlyContinue
$results.Add((Result 'dinstar-web' $(if ($web.TcpTestSucceeded) { 'PASS' } else { 'FAIL' }) "TCP $WebPort reachable=$($web.TcpTestSucceeded)"))

$sipListeners = @(Get-NetUDPEndpoint -LocalPort $SipPort -ErrorAction SilentlyContinue)
$results.Add((Result 'asterisk-udp' $(if ($sipListeners.Count -gt 0) { 'PASS' } else { 'FAIL' }) "UDP $SipPort listeners=$($sipListeners.Count)"))

$firewallRules = @(Get-NetFirewallRule -DisplayName 'YOUNES DINSTAR *' -ErrorAction SilentlyContinue)
$results.Add((Result 'dinstar-firewall' $(if ($firewallRules.Count -ge 2) { 'PASS' } else { 'WARN' }) "Expected SIP/RTP rules found=$($firewallRules.Count)"))

if (Get-Command docker -ErrorAction SilentlyContinue) {
    $containers = @(docker ps --format '{{.Names}} {{.Status}}' 2>$null | Where-Object { $_ -match 'red-pstn-gateway|red-backend' })
    $results.Add((Result 'docker-services' $(if ($containers.Count -ge 2) { 'PASS' } else { 'FAIL' }) (($containers -join '; ') ?? 'red-pstn-gateway/red-backend not both running')))
} else {
    $results.Add((Result 'docker-services' 'FAIL' 'Docker is not available on PATH'))
}

$results | Format-Table -AutoSize
$failed = @($results | Where-Object { $_.status -eq 'FAIL' }).Count
if ($failed -gt 0) {
    Write-Error "DINSTAR LAN preflight failed ($failed mandatory check(s)). Resolve the items above before changing UC2000 SIP settings or making a PSTN test call."
}
