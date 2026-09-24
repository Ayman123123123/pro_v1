$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Runtime.InteropServices
$base = 'https://192.168.11.2'
$jar = Join-Path $env:TEMP ('dinstar-cookie-' + [guid]::NewGuid().ToString() + '.txt')
$loginBody = Join-Path $env:TEMP ('dinstar-login-' + [guid]::NewGuid().ToString() + '.txt')
$deleteBody = Join-Path $env:TEMP ('dinstar-delete-rule-' + [guid]::NewGuid().ToString() + '.html')
$securePassword = Read-Host 'DINSTAR admin password' -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    $loginStatus = & curl.exe -sS -k -c $jar -o $loginBody -w '%{http_code}' --connect-timeout 3 --max-time 10 --data-urlencode 'username=admin' --data-urlencode ("password={0}" -f $plainPassword) "$base/goform/IADIdentityAuth"
    if ($loginStatus -ne '302') { throw "DINSTAR login failed with HTTP $loginStatus" }
    $deleteStatus = & curl.exe -sS -k -b $jar -o $deleteBody -w '%{http_code}' --connect-timeout 3 --max-time 12 --data-urlencode 'HBPhoneNumberRuleEnable0=on' --data-urlencode 'Del=Del' --data-urlencode 'eeee=on' "$base/goform/HBPhoneNumberRuleDelete"
    if ($deleteStatus -notin @('200','302')) { throw "DINSTAR rule deletion failed with HTTP $deleteStatus" }
    $verify = @(& curl.exe -sS -k -b $jar --connect-timeout 3 --max-time 10 "$base/enHBPhoneNumber.htm") -join "`n"
    $removed = -not ($verify -match "HBPhoneNumberRule0" -and $verify -match ">333<" -and $verify -match ">MMN<")
    Write-Output ("RULE_DELETE_HTTP={0}" -f $deleteStatus)
    Write-Output ("RULE_REMOVED={0}" -f $removed)
    if (-not $removed) { throw 'Rule remained visible after delete request; investigate before any further action.' }
}
finally {
    if ($ptr -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
    Remove-Variable plainPassword -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $jar,$loginBody,$deleteBody -Force -ErrorAction SilentlyContinue
}
