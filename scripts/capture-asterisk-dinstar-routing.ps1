$ErrorActionPreference = 'Stop'
$outFile = 'C:\Users\hpc01\Pictures\pro_new\ASTERISK_DINSTAR_ROUTING_SANITIZED_2026-08-23.txt'
$command = "grep -R -n -E '(dinstar|DINSTAR|192\\.168\\.11\\.(2|3)|PJSIP/[0-7])' /etc/asterisk --include='*.conf' 2>/dev/null || true"
$lines = @(& docker.exe exec red-pstn-gateway sh -lc $command 2>&1)
$sanitized = $lines | ForEach-Object {
    $_ -replace '(?i)(secret|password|auth_password|md5secret)(\s*=\s*)([^\s;]+)', '$1$2***REDACTED***'
}
$sanitized | Set-Content -LiteralPath $outFile -Encoding UTF8
Write-Output ("ROUTING_FILE={0}" -f $outFile)
Write-Output ("MATCHED_LINES={0}" -f @($sanitized).Count)
