$ErrorActionPreference = 'Stop'
$backupDir = 'C:\Users\hpc01\Pictures\pro_new\backups\asterisk-dinstar-2026-08-23-pre-route-fix'
New-Item -ItemType Directory -Path $backupDir -Force | Out-Null
& docker.exe cp 'red-pstn-gateway:/etc/asterisk/pjsip.conf' (Join-Path $backupDir 'pjsip.conf')
& docker.exe cp 'red-pstn-gateway:/etc/asterisk/extensions.conf' (Join-Path $backupDir 'extensions.conf')
& docker.exe cp 'red-pstn-gateway:/etc/asterisk/rtp.conf' (Join-Path $backupDir 'rtp.conf')
Get-FileHash -Algorithm SHA256 (Join-Path $backupDir '*.conf') | Select-Object Path,Hash | Format-Table -AutoSize
Write-Output ("BACKUP_DIR={0}" -f $backupDir)
