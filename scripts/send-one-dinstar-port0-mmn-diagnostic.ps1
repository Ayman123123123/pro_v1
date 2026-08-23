$ErrorActionPreference = 'Stop'
$base = 'https://192.168.11.2'
$envFile = 'C:\Users\hpc01\Pictures\pro_new\RED_Ultimate_V1-main\RED_Ultimate\.env'
$root = 'C:\Users\hpc01\Pictures\pro_new'
$dispatchFile = Join-Path $root 'DINSTAR_SMS_DIRECT_DISPATCH_2026-08-23.txt'
$detailFile = Join-Path $root 'DINSTAR_SMS_DETAIL_REPORT_AFTER_DIAGNOSTIC_2026-08-23.html'
$outboxFile = Join-Path $root 'DINSTAR_SMS_OUTBOX_AFTER_DIAGNOSTIC_2026-08-23.html'
$jar = Join-Path $env:TEMP ('dinstar-cookie-' + [guid]::NewGuid().ToString() + '.txt')
$loginBody = Join-Path $env:TEMP ('dinstar-login-' + [guid]::NewGuid().ToString() + '.txt')
$sendBody = Join-Path $env:TEMP ('dinstar-sms-send-' + [guid]::NewGuid().ToString() + '.html')
$sendHeaders = Join-Path $env:TEMP ('dinstar-sms-send-headers-' + [guid]::NewGuid().ToString() + '.txt')

try {
    $passwordLine = Get-Content -LiteralPath $envFile | Where-Object { $_ -match '^DINSTAR_PASSWORD=' } | Select-Object -First 1
    if (-not $passwordLine) { throw 'DINSTAR password is unavailable in the runtime environment.' }
    $password = $passwordLine.Substring('DINSTAR_PASSWORD='.Length)
    $loginStatus = & curl.exe -sS -k -c $jar -o $loginBody -w '%{http_code}' --connect-timeout 3 --max-time 10 --retry 0 --data-urlencode 'username=admin' --data-urlencode ("password={0}" -f $password) "$base/goform/IADIdentityAuth"
    if ($loginStatus -ne '302') { throw "DINSTAR login failed with HTTP $loginStatus" }

    $sentAt = (Get-Date).ToString('o')
    # Exactly one POST. Do not add --location, --retry, Number Learning, or any loop.
    $sendStatus = & curl.exe -sS -k -b $jar -c $jar -D $sendHeaders -o $sendBody -w '%{http_code}' --connect-timeout 3 --max-time 20 --retry 0 --data-urlencode 'Index0=on' --data-urlencode 'SendMode=0' --data-urlencode 'Addressee=333' --data-urlencode 'Encoding=1' --data-urlencode 'MsgInfo=MMN' --data-urlencode 'ok=Send' "$base/goform/WIAMsgSend"
    $location = (Get-Content -LiteralPath $sendHeaders | Where-Object { $_ -match '^Location:' } | Select-Object -Last 1).Trim()
    @(
      "SENT_AT=$sentAt"
      'INTENT=one_direct_sms_only'
      'PORT=0'
      'DESTINATION=333'
      'MESSAGE=MMN'
      'ENCODING=GSM_7BIT'
      "LOGIN_HTTP=$loginStatus"
      "SEND_HTTP=$sendStatus"
      "SEND_REDIRECT=$location"
    ) | Out-File -LiteralPath $dispatchFile -Encoding utf8
    if ($sendStatus -notin @('200','302')) { throw "DINSTAR direct SMS request failed with HTTP $sendStatus" }

    Start-Sleep -Seconds 8
    $detailStatus = & curl.exe -sS -k -b $jar -o $detailFile -w '%{http_code}' --connect-timeout 3 --max-time 10 --retry 0 "$base/enMsgSMS.htm"
    $outboxStatus = & curl.exe -sS -k -b $jar -o $outboxFile -w '%{http_code}' --connect-timeout 3 --max-time 10 --retry 0 "$base/enSmsSendRecord.htm"
    Add-Content -LiteralPath $dispatchFile -Value "DETAIL_REPORT_HTTP=$detailStatus" -Encoding utf8
    Add-Content -LiteralPath $dispatchFile -Value "OUTBOX_HTTP=$outboxStatus" -Encoding utf8

    Write-Output 'SMS_POST_COUNT=1'
    Write-Output "SMS_SEND_HTTP=$sendStatus"
    if ($location) { Write-Output "SMS_SEND_REDIRECT=$location" }
    Write-Output "DISPATCH_FILE=$dispatchFile"
    Write-Output "DETAIL_FILE=$detailFile"
    Write-Output "OUTBOX_FILE=$outboxFile"
}
finally {
    Remove-Variable password -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $jar,$loginBody,$sendBody,$sendHeaders -Force -ErrorAction SilentlyContinue
}
