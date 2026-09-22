$ErrorActionPreference = 'Stop'
$outFile = 'C:\Users\hpc01\Pictures\pro_new\ASTERISK_DINSTAR_CONFIG_SANITIZED_2026-08-23.txt'
$command = @'
{
  echo '=== /etc/asterisk/pjsip.conf lines 1-250 ==='
  sed -n '1,250p' /etc/asterisk/pjsip.conf
  echo '=== /etc/asterisk/extensions.conf lines 1-250 ==='
  sed -n '1,250p' /etc/asterisk/extensions.conf
} | sed -E 's/^([[:space:]]*(secret|password|auth_password|md5secret)[[:space:]]*=[[:space:]]*).*/\1***REDACTED***/I'
'@
$lines = @(& docker.exe exec red-pstn-gateway sh -lc $command 2>&1)
$lines | Set-Content -LiteralPath $outFile -Encoding UTF8
Write-Output ("CONFIG_FILE={0}" -f $outFile)
