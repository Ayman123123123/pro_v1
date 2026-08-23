$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Runtime.InteropServices
$base = 'https://192.168.11.2'
$outFile = 'C:\Users\hpc01\Pictures\pro_new\DINSTAR_CONFIG_BACKUP_BEFORE_NUMBER_LEARNING_2026-08-23.cfg'
$jar = Join-Path $env:TEMP ('dinstar-cookie-' + [guid]::NewGuid().ToString() + '.txt')
$loginBody = Join-Path $env:TEMP ('dinstar-login-' + [guid]::NewGuid().ToString() + '.txt')
$securePassword = Read-Host 'DINSTAR admin password' -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    $loginStatus = & curl.exe -sS -k -c $jar -o $loginBody -w '%{http_code}' --connect-timeout 3 --max-time 10 --data-urlencode 'username=admin' --data-urlencode ("password={0}" -f $plainPassword) "$base/goform/IADIdentityAuth"
    if ($loginStatus -ne '302') { throw "DINSTAR login failed with HTTP $loginStatus" }
    $backupStatus = & curl.exe -sS -k -b $jar -o $outFile -w '%{http_code}' --connect-timeout 3 --max-time 20 "$base/backup.cfg"
    if ($backupStatus -ne '200') { throw "DINSTAR backup failed with HTTP $backupStatus" }
    $item = Get-Item -LiteralPath $outFile
    if ($item.Length -lt 100) { throw 'DINSTAR backup file is unexpectedly small' }
    $hash = Get-FileHash -LiteralPath $outFile -Algorithm SHA256
    Write-Output ("BACKUP_FILE={0}" -f $outFile)
    Write-Output ("BACKUP_BYTES={0}" -f $item.Length)
    Write-Output ("BACKUP_SHA256={0}" -f $hash.Hash)
}
finally {
    if ($ptr -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
    Remove-Variable plainPassword -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $jar,$loginBody -Force -ErrorAction SilentlyContinue
}
