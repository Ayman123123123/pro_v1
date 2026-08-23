$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Runtime.InteropServices
$base = 'https://192.168.11.2'
$page = 'enHBPhoneNumber.htm'
$jar = Join-Path $env:TEMP ('dinstar-cookie-' + [guid]::NewGuid().ToString() + '.txt')
$loginBody = Join-Path $env:TEMP ('dinstar-login-' + [guid]::NewGuid().ToString() + '.txt')
$bodyFile = Join-Path $env:TEMP ('dinstar-learning-' + [guid]::NewGuid().ToString() + '.html')
$securePassword = Read-Host 'DINSTAR admin password' -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    $loginStatus = & curl.exe -sS -k -c $jar -o $loginBody -w '%{http_code}' --connect-timeout 3 --max-time 10 --data-urlencode 'username=admin' --data-urlencode ("password={0}" -f $plainPassword) "$base/goform/IADIdentityAuth"
    if ($loginStatus -ne '302') { throw "DINSTAR login failed with HTTP $loginStatus" }
    $pageStatus = & curl.exe -sS -k -b $jar -o $bodyFile -w '%{http_code}' --connect-timeout 3 --max-time 10 "$base/$page"
    Write-Output ("PAGE_HTTP={0}" -f $pageStatus)
    $html = Get-Content -LiteralPath $bodyFile -Raw
    $savedForm = 'C:\Users\hpc01\Pictures\pro_new\DINSTAR_PHONE_NUMBER_LEARNING_FORM_2026-08-23.html'
    Set-Content -LiteralPath $savedForm -Value $html -Encoding UTF8
    Write-Output ("SAVED_FORM={0}" -f $savedForm)
    Write-Output '=== FORM_ACTIONS ==='
    [regex]::Matches($html, '<form[^>]*>') | ForEach-Object { $_.Value }
    Write-Output '=== INPUTS ==='
    [regex]::Matches($html, '<(input|select|textarea)[^>]*>') | ForEach-Object { $_.Value }
    Write-Output '=== RELEVANT_SCRIPT_LINES ==='
    $html -split "`n" | Where-Object { $_ -match 'goform|submit|phone|number|learn|study|ussd|sms|keyword|test' } | ForEach-Object { $_.Trim() }
}
finally {
    if ($ptr -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
    Remove-Variable plainPassword -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $jar,$loginBody,$bodyFile -Force -ErrorAction SilentlyContinue
}
