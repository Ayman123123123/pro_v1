$ErrorActionPreference = 'Stop'
$base = 'https://192.168.11.2'
$envFile = 'C:\Users\hpc01\Pictures\pro_new\RED_Ultimate_V1-main\RED_Ultimate\.env'
$jar = Join-Path $env:TEMP ('dinstar-cookie-' + [guid]::NewGuid().ToString() + '.txt')
$loginBody = Join-Path $env:TEMP ('dinstar-login-' + [guid]::NewGuid().ToString() + '.txt')
$startBody = Join-Path $env:TEMP ('dinstar-module-log-start-' + [guid]::NewGuid().ToString() + '.html')
$headers = Join-Path $env:TEMP ('dinstar-module-log-headers-' + [guid]::NewGuid().ToString() + '.txt')

try {
    $passwordLine = Get-Content -LiteralPath $envFile | Where-Object { $_ -match '^DINSTAR_PASSWORD=' } | Select-Object -First 1
    if (-not $passwordLine) { throw 'DINSTAR password is unavailable in the runtime environment.' }
    $password = $passwordLine.Substring('DINSTAR_PASSWORD='.Length)

    $loginStatus = & curl.exe -sS -k -c $jar -o $loginBody -w '%{http_code}' --connect-timeout 3 --max-time 10 --data-urlencode 'username=admin' --data-urlencode ("password={0}" -f $password) "$base/goform/IADIdentityAuth"
    if ($loginStatus -ne '302') { throw "DINSTAR login failed with HTTP $loginStatus" }

    $startStatus = & curl.exe -sS -k -b $jar -c $jar -D $headers -o $startBody -w '%{http_code}' --connect-timeout 3 --max-time 20 --data-urlencode 'ModuleLogStatus=IDLE' --data-urlencode 'Port=0' --data-urlencode 'Select=0' --data-urlencode 'Start=Start' "$base/goform/ModuleLogStart"
    if ($startStatus -notin @('200','302')) { throw "DINSTAR module-log start failed with HTTP $startStatus" }

    Start-Sleep -Seconds 2
    $state = & curl.exe -sS -k -b $jar --connect-timeout 3 --max-time 10 "$base/EiaGetModuleLogStatus"
    $location = (Get-Content -LiteralPath $headers | Where-Object { $_ -match '^Location:' } | Select-Object -Last 1).Trim()

    Write-Output ("MODULE_LOG_START_HTTP={0}" -f $startStatus)
    if ($location) { Write-Output ("MODULE_LOG_REDIRECT={0}" -f $location) }
    Write-Output ("MODULE_LOG_RAW_STATE={0}" -f $state)
    if ($state -notmatch '"?status"?\s*:\s*1') { throw 'DINSTAR module log did not enter RECORDING state.' }
    Write-Output 'MODULE_LOG_PORT=0'
    Write-Output 'MODULE_LOG_TYPE=general'
    Write-Output 'MODULE_LOG_STATE=RECORDING'
}
finally {
    Remove-Variable password -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $jar,$loginBody,$startBody,$headers -Force -ErrorAction SilentlyContinue
}
