$ErrorActionPreference = 'Stop'
$base = 'https://192.168.11.2'
$envFile = 'C:\Users\hpc01\Pictures\pro_new\RED_Ultimate_V1-main\RED_Ultimate\.env'
$outFile = 'C:\Users\hpc01\Pictures\pro_new\DINSTAR_SMS_SEND_FORM_CURRENT_2026-08-23.html'
$jar = Join-Path $env:TEMP ('dinstar-cookie-' + [guid]::NewGuid().ToString() + '.txt')
$loginBody = Join-Path $env:TEMP ('dinstar-login-' + [guid]::NewGuid().ToString() + '.txt')

try {
    $passwordLine = Get-Content -LiteralPath $envFile | Where-Object { $_ -match '^DINSTAR_PASSWORD=' } | Select-Object -First 1
    if (-not $passwordLine) { throw 'DINSTAR password is unavailable in the runtime environment.' }
    $password = $passwordLine.Substring('DINSTAR_PASSWORD='.Length)
    $loginStatus = & curl.exe -sS -k -c $jar -o $loginBody -w '%{http_code}' --connect-timeout 3 --max-time 10 --data-urlencode 'username=admin' --data-urlencode ("password={0}" -f $password) "$base/goform/IADIdentityAuth"
    if ($loginStatus -ne '302') { throw "DINSTAR login failed with HTTP $loginStatus" }
    $formStatus = & curl.exe -sS -k -b $jar -o $outFile -w '%{http_code}' --connect-timeout 3 --max-time 10 "$base/enWIASendMsg.htm"
    if ($formStatus -ne '200') { throw "DINSTAR SMS send form fetch failed with HTTP $formStatus" }
    Write-Output "SMS_SEND_FORM_HTTP=$formStatus"
    Write-Output "SMS_SEND_FORM_FILE=$outFile"
}
finally {
    Remove-Variable password -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $jar,$loginBody -Force -ErrorAction SilentlyContinue
}
