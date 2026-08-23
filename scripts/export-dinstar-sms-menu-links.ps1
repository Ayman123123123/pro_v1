$ErrorActionPreference = 'Stop'
$base = 'https://192.168.11.2'
$envFile = 'C:\Users\hpc01\Pictures\pro_new\RED_Ultimate_V1-main\RED_Ultimate\.env'
$outFile = 'C:\Users\hpc01\Pictures\pro_new\DINSTAR_MENU_SMS_LINKS_2026-08-23.txt'
$jar = Join-Path $env:TEMP ('dinstar-cookie-' + [guid]::NewGuid().ToString() + '.txt')
$loginBody = Join-Path $env:TEMP ('dinstar-login-' + [guid]::NewGuid().ToString() + '.txt')
$menuBody = Join-Path $env:TEMP ('dinstar-menu-' + [guid]::NewGuid().ToString() + '.html')

try {
    $passwordLine = Get-Content -LiteralPath $envFile | Where-Object { $_ -match '^DINSTAR_PASSWORD=' } | Select-Object -First 1
    if (-not $passwordLine) { throw 'DINSTAR password is unavailable in the runtime environment.' }
    $password = $passwordLine.Substring('DINSTAR_PASSWORD='.Length)
    $loginStatus = & curl.exe -sS -k -c $jar -o $loginBody -w '%{http_code}' --connect-timeout 3 --max-time 10 --data-urlencode 'username=admin' --data-urlencode ("password={0}" -f $password) "$base/goform/IADIdentityAuth"
    if ($loginStatus -ne '302') { throw "DINSTAR login failed with HTTP $loginStatus" }

    $menuStatus = & curl.exe -sS -k -b $jar -o $menuBody -w '%{http_code}' --connect-timeout 3 --max-time 10 "$base/enMenu.htm"
    if ($menuStatus -ne '200') { throw "DINSTAR menu fetch failed with HTTP $menuStatus" }
    $matches = Select-String -LiteralPath $menuBody -Pattern 'sms|message' -CaseSensitive:$false | ForEach-Object { $_.Line.Trim() }
    @(
      "MENU_HTTP=$menuStatus"
      '--- SMS_OR_MESSAGE_LINK_LINES ---'
      $matches
    ) | Out-File -LiteralPath $outFile -Encoding utf8
    Write-Output "MENU_LINKS_FILE=$outFile"
    Write-Output ("MATCH_COUNT=" + @($matches).Count)
}
finally {
    Remove-Variable password -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $jar,$loginBody,$menuBody -Force -ErrorAction SilentlyContinue
}
