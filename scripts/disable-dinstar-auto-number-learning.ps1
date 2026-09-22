$ErrorActionPreference = 'Stop'
$envFile = 'C:\Users\hpc01\Pictures\pro_new\RED_Ultimate_V1-main\RED_Ultimate\.env'
$key = 'RED_DINSTAR_NUMBER_LEARNING_AUTO_ENABLED'
$lines = Get-Content -LiteralPath $envFile
$found = $false
$updated = foreach ($line in $lines) {
    if ($line -match "^$key=") {
        $found = $true
        "$key=false"
    } else {
        $line
    }
}
if (-not $found) { $updated += "$key=false" }
[System.IO.File]::WriteAllLines($envFile, [string[]]$updated, [System.Text.UTF8Encoding]::new($false))
$value = Get-Content -LiteralPath $envFile | Where-Object { $_ -match "^$key=" } | Select-Object -First 1
if ($value -ne "$key=false") { throw 'Auto Number Learning disable flag verification failed.' }
Write-Output 'DINSTAR_AUTO_NUMBER_LEARNING=false'
