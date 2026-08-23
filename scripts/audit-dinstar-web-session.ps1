$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Runtime.InteropServices
$base = 'https://192.168.11.2'
$jar = Join-Path $env:TEMP ('dinstar-cookie-' + [guid]::NewGuid().ToString() + '.txt')
$loginBody = Join-Path $env:TEMP ('dinstar-login-' + [guid]::NewGuid().ToString() + '.txt')
$portsBody = Join-Path $env:TEMP ('dinstar-ports-' + [guid]::NewGuid().ToString() + '.json')
$securePassword = Read-Host 'DINSTAR admin password' -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    $loginStatus = & curl.exe -sS -k -c $jar -o $loginBody -w '%{http_code}' --connect-timeout 3 --max-time 10 --data-urlencode 'username=admin' --data-urlencode ("password={0}" -f $plainPassword) "$base/goform/IADIdentityAuth"
    Write-Output ("LOGIN_HTTP={0}" -f $loginStatus)
    $portsStatus = & curl.exe -sS -k -b $jar -o $portsBody -w '%{http_code}' --connect-timeout 3 --max-time 10 -H 'Accept: application/json' "$base/WebGetPortInfoAll"
    Write-Output ("PORT_INFO_HTTP={0}" -f $portsStatus)
    Get-Content -LiteralPath $portsBody -Raw
}
finally {
    if ($ptr -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
    Remove-Variable plainPassword -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $jar,$loginBody,$portsBody -Force -ErrorAction SilentlyContinue
}
