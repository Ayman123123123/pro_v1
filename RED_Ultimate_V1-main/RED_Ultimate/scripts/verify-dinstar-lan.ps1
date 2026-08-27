<#
.SYNOPSIS
  Read-only preflight for the RED <-> DINSTAR UC2000-VE LAN path.

.DESCRIPTION
  Verifies that the Windows host has a management-NIC address in the same
  subnet as the UC2000-VE, that the gateway web port is reachable, that
  Asterisk/Docker and UDP 5060 are present, and that the restrictive firewall
  rules for the gateway are installed.

  It deliberately does not edit network configuration, restart containers,
  send SMS, originate a call, or change any DINSTAR setting. Every check is
  a read.

.NOTES
  Targets Windows PowerShell 5.1 (the shell shipped with Windows). The
  original revision used the PowerShell 7 null-coalescing operator `??`,
  which is a *parse* error on 5.1 — the script could not run at all on the
  very host it was written for. Subnet detection is also derived from
  -DinstarIp instead of a hard-coded 192.168.11.* so a relocated gateway is
  still validated.

.EXAMPLE
  .\verify-dinstar-lan.ps1 -DinstarIp 192.168.11.2 -ExpectedAsteriskIp 192.168.11.10
#>
param(
    [string]$DinstarIp = '192.168.11.2',
    [string]$ExpectedAsteriskIp = '',
    [ValidateRange(1, 65535)][int]$WebPort = 443,
    [ValidateRange(1, 65535)][int]$SipPort = 5060
)

$ErrorActionPreference = 'Stop'

function New-CheckResult {
    param([string]$Name, [string]$Status, [string]$Detail)
    [pscustomobject]@{ check = $Name; status = $Status; detail = $Detail }
}

# /24 من عنوان البوابة نفسه: البوابة المنقولة تبقى مُتحقَّقًا منها.
$octets = $DinstarIp.Split('.')
if ($octets.Count -ne 4) { throw "DinstarIp is not a dotted IPv4 address: $DinstarIp" }
$subnetPrefix = "$($octets[0]).$($octets[1]).$($octets[2])."

$results = [System.Collections.Generic.List[object]]::new()

# ── 1. بطاقة الإدارة في نفس الشبكة الفرعية ────────────────────────────────
$managementAddresses = @(
    Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
        Where-Object { $_.IPAddress.StartsWith($subnetPrefix) -and $_.IPAddress -ne $DinstarIp }
)

if ($managementAddresses.Count -eq 0) {
    $results.Add((New-CheckResult 'management-nic' 'FAIL' "No host address in ${subnetPrefix}0/24 besides $DinstarIp"))
} else {
    $addresses = ($managementAddresses | ForEach-Object { "$($_.IPAddress) on $($_.InterfaceAlias)" }) -join '; '
    if ($ExpectedAsteriskIp -and -not ($managementAddresses.IPAddress -contains $ExpectedAsteriskIp)) {
        $results.Add((New-CheckResult 'management-nic' 'FAIL' "Expected Asterisk IP $ExpectedAsteriskIp is not assigned; found $addresses"))
    } else {
        $results.Add((New-CheckResult 'management-nic' 'PASS' $addresses))
    }
}

# ── 2. منفذ إدارة البوابة ─────────────────────────────────────────────────
$webReachable = $false
$webDetail = "TCP $WebPort probe did not complete"
try {
    $web = Test-NetConnection -ComputerName $DinstarIp -Port $WebPort -WarningAction SilentlyContinue -ErrorAction Stop
    $webReachable = [bool]$web.TcpTestSucceeded
    $webDetail = "TCP $WebPort reachable=$webReachable"
} catch {
    $webDetail = "TCP $WebPort probe failed: $($_.Exception.Message)"
}
$results.Add((New-CheckResult 'dinstar-web' $(if ($webReachable) { 'PASS' } else { 'FAIL' }) $webDetail))

# ── 3. مُستمع SIP على UDP ─────────────────────────────────────────────────
$sipListeners = @(Get-NetUDPEndpoint -LocalPort $SipPort -ErrorAction SilentlyContinue)
$results.Add((New-CheckResult 'asterisk-udp' $(if ($sipListeners.Count -gt 0) { 'PASS' } else { 'FAIL' }) "UDP $SipPort listeners=$($sipListeners.Count)"))

# ── 4. قواعد الجدار المقيِّدة ──────────────────────────────────────────────
# WARN لا FAIL: غياب القواعد لا يمنع الاختبار، لكنه يعني مسارًا غير مقيَّد.
$firewallRules = @(Get-NetFirewallRule -DisplayName 'YOUNES DINSTAR *' -ErrorAction SilentlyContinue)
$results.Add((New-CheckResult 'dinstar-firewall' $(if ($firewallRules.Count -ge 2) { 'PASS' } else { 'WARN' }) "Expected SIP/RTP rules found=$($firewallRules.Count)"))

# ── 5. حاويات الخدمة ──────────────────────────────────────────────────────
if (Get-Command docker -ErrorAction SilentlyContinue) {
    $containers = @(docker ps --format '{{.Names}} {{.Status}}' 2>$null | Where-Object { $_ -match 'red-pstn-gateway|red-backend' })
    if ($containers.Count -ge 2) {
        $results.Add((New-CheckResult 'docker-services' 'PASS' ($containers -join '; ')))
    } elseif ($containers.Count -gt 0) {
        $results.Add((New-CheckResult 'docker-services' 'FAIL' "Only running: $($containers -join '; ') — need both red-pstn-gateway and red-backend"))
    } else {
        $results.Add((New-CheckResult 'docker-services' 'FAIL' 'Neither red-pstn-gateway nor red-backend is running'))
    }
} else {
    $results.Add((New-CheckResult 'docker-services' 'FAIL' 'Docker is not available on PATH'))
}

$results | Format-Table -AutoSize

$failed = @($results | Where-Object { $_.status -eq 'FAIL' }).Count
$warned = @($results | Where-Object { $_.status -eq 'WARN' }).Count
if ($warned -gt 0) {
    Write-Warning "$warned advisory check(s) did not pass. The LAN path may be unrestricted."
}
if ($failed -gt 0) {
    Write-Error "DINSTAR LAN preflight failed ($failed mandatory check(s)). Resolve the items above before changing UC2000 SIP settings or making a PSTN test call."
    exit 1
}
Write-Host 'DINSTAR LAN preflight passed. Read-only: nothing was changed.' -ForegroundColor Green
