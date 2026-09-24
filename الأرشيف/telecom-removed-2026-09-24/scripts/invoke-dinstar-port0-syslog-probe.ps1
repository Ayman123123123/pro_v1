$ErrorActionPreference = 'Stop'
$base = 'https://192.168.11.2'
$envFile = 'C:\Users\hpc01\Pictures\pro_new\RED_Ultimate_V1-main\RED_Ultimate\.env'
$jar = Join-Path $env:TEMP ('dinstar-cookie-' + [guid]::NewGuid().ToString() + '.txt')
$loginBody = Join-Path $env:TEMP ('dinstar-login-' + [guid]::NewGuid().ToString() + '.txt')
$restartBody = Join-Path $env:TEMP ('dinstar-restart-' + [guid]::NewGuid().ToString() + '.html')

try {
    $passwordLine = Get-Content -LiteralPath $envFile | Where-Object { $_ -match '^DINSTAR_PASSWORD=' } | Select-Object -First 1
    if (-not $passwordLine) { throw 'DINSTAR password is unavailable in the runtime environment.' }
    $password = $passwordLine.Substring('DINSTAR_PASSWORD='.Length)

    $loginStatus = & curl.exe -sS -k -c $jar -o $loginBody -w '%{http_code}' --connect-timeout 3 --max-time 10 --data-urlencode 'username=admin' --data-urlencode ("password={0}" -f $password) "$base/goform/IADIdentityAuth"
    if ($loginStatus -ne '302') { throw "DINSTAR login failed with HTTP $loginStatus" }

    $restartStatus = & curl.exe -sS -k -b $jar -o $restartBody -w '%{http_code}' --connect-timeout 3 --max-time 20 --data-urlencode 'PortNo=0' "$base/goform/SimGotoRestart"
    if ($restartStatus -notin @('200','302')) { throw "DINSTAR port-0 restart failed with HTTP $restartStatus" }

    Write-Output ("PORT_RESTART_HTTP={0}" -f $restartStatus)
    Write-Output 'PORT_RESTART_REQUESTED=0'
}
finally {
    Remove-Variable password -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $jar,$loginBody,$restartBody -Force -ErrorAction SilentlyContinue
}
