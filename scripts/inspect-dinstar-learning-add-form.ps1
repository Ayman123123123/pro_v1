$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Runtime.InteropServices
$base = 'https://192.168.11.2'
$page = 'enServiceCfg.htm'
$savePath = 'C:\Users\hpc01\Pictures\pro_new\DINSTAR_SERVICE_CONFIG_2026-08-23.html'
$jar = Join-Path $env:TEMP ('dinstar-cookie-' + [guid]::NewGuid().ToString() + '.txt')
$loginBody = Join-Path $env:TEMP ('dinstar-login-' + [guid]::NewGuid().ToString() + '.txt')
$bodyFile = Join-Path $env:TEMP ('dinstar-learning-add-' + [guid]::NewGuid().ToString() + '.html')
$securePassword = Read-Host 'DINSTAR admin password' -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    $loginStatus = & curl.exe -sS -k -c $jar -o $loginBody -w '%{http_code}' --connect-timeout 3 --max-time 10 --data-urlencode 'username=admin' --data-urlencode ("password={0}" -f $plainPassword) "$base/goform/IADIdentityAuth"
    if ($loginStatus -ne '302') { throw "DINSTAR login failed with HTTP $loginStatus" }
    $pageStatus = & curl.exe -sS -k -b $jar -o $bodyFile -w '%{http_code}' --connect-timeout 3 --max-time 10 "$base/$page"
    Write-Output ("PAGE_HTTP={0}" -f $pageStatus)
    $html = Get-Content -LiteralPath $bodyFile -Raw
    Set-Content -LiteralPath $savePath -Value $html -Encoding UTF8
    Write-Output ("SAVED_FORM={0}" -f $savePath)
}
finally {
    if ($ptr -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
    Remove-Variable plainPassword -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $jar,$loginBody,$bodyFile -Force -ErrorAction SilentlyContinue
}
