param([Parameter(Mandatory=$true)][ValidateRange(0,7)][int]$PortNo)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Runtime.InteropServices
$base = 'https://192.168.11.2'
$jar = Join-Path $env:TEMP ('dinstar-cookie-' + [guid]::NewGuid().ToString() + '.txt')
$loginBody = Join-Path $env:TEMP ('dinstar-login-' + [guid]::NewGuid().ToString() + '.txt')
$restartBody = Join-Path $env:TEMP ('dinstar-restart-' + [guid]::NewGuid().ToString() + '.html')
$securePassword = Read-Host 'DINSTAR admin password' -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    $loginStatus = & curl.exe -sS -k -c $jar -o $loginBody -w '%{http_code}' --connect-timeout 3 --max-time 10 --data-urlencode 'username=admin' --data-urlencode ("password={0}" -f $plainPassword) "$base/goform/IADIdentityAuth"
    if ($loginStatus -ne '302') { throw "DINSTAR login failed with HTTP $loginStatus" }
    $restartStatus = & curl.exe -sS -k -b $jar -o $restartBody -w '%{http_code}' --connect-timeout 3 --max-time 20 --data-urlencode ("PortNo={0}" -f $PortNo) "$base/goform/SimGotoRestart"
    if ($restartStatus -notin @('200','302')) { throw "DINSTAR port restart failed with HTTP $restartStatus" }
    Write-Output ("PORT_RESTART_HTTP={0}" -f $restartStatus)
    Write-Output ("PORT_RESTART_REQUESTED={0}" -f $PortNo)
}
finally {
    if ($ptr -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
    Remove-Variable plainPassword -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $jar,$loginBody,$restartBody -Force -ErrorAction SilentlyContinue
}
