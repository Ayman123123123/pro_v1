$ErrorActionPreference = 'Stop'
$name = 'DINSTAR Temporary Syslog Capture UDP 514'
& netsh.exe advfirewall firewall delete rule name="$name" | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Failed to remove temporary DINSTAR syslog firewall rule.' }
Write-Output 'TEMPORARY_DINSTAR_SYSLOG_FIREWALL_RULE_REMOVED'
