$ErrorActionPreference = 'Stop'
$outFile = 'C:\Users\hpc01\Pictures\pro_new\ASTERISK_DINSTAR_ERRORS_2026-08-23.txt'
$command = "tail -n 1500 /var/log/asterisk/full 2>/dev/null | grep -Ei '(dinstar|192\\.168\\.11\\.(2|3)|pjsip|rtp|no route|403|404|480|486|500|503|unreachable|failed|reject)' | tail -n 350 || true"
$lines = @(& docker.exe exec red-pstn-gateway sh -lc $command 2>&1)
$sanitized = @($lines | ForEach-Object { $_ -replace '(?i)(password|secret|authorization)([=: ]+)([^ ]+)', '$1$2***REDACTED***' })
if ($sanitized.Count -eq 0) { $sanitized = @('# No matching DINSTAR/SIP/RTP error lines in the last 1500 Asterisk log entries.') }
Set-Content -LiteralPath $outFile -Value $sanitized -Encoding UTF8
Write-Output ("ERROR_LOG_FILE={0}" -f $outFile)
