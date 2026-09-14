$ErrorActionPreference = 'Stop'
$envFile = 'C:\Users\hpc01\Pictures\pro_new\RED_Ultimate_V1-main\RED_Ultimate\.env'
$lines = Get-Content -LiteralPath $envFile
$found = $false
$updated = foreach ($line in $lines) {
    if ($line -match '^DINSTAR_IPS=') {
        $found = $true
        'DINSTAR_IPS=192.168.11.2'
    } else {
        $line
    }
}
if (-not $found) { $updated += 'DINSTAR_IPS=192.168.11.2' }
[System.IO.File]::WriteAllLines($envFile, [string[]]$updated, [System.Text.UTF8Encoding]::new($false))
$active = Get-Content -LiteralPath $envFile | Where-Object { $_ -match '^DINSTAR_IP(S)?=' }
if ($active -notcontains 'DINSTAR_IP=192.168.11.2' -or $active -notcontains 'DINSTAR_IPS=192.168.11.2') {
    throw 'DINSTAR active IP verification failed.'
}
Write-Output 'DINSTAR_ACTIVE_FLEET=192.168.11.2'
