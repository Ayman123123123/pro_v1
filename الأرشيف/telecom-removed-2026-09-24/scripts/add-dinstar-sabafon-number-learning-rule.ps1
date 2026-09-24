$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Runtime.InteropServices
$base = 'https://192.168.11.2'
$jar = Join-Path $env:TEMP ('dinstar-cookie-' + [guid]::NewGuid().ToString() + '.txt')
$loginBody = Join-Path $env:TEMP ('dinstar-login-' + [guid]::NewGuid().ToString() + '.txt')
$ruleBody = Join-Path $env:TEMP ('dinstar-rule-' + [guid]::NewGuid().ToString() + '.html')
$securePassword = Read-Host 'DINSTAR admin password' -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    $loginStatus = & curl.exe -sS -k -c $jar -o $loginBody -w '%{http_code}' --connect-timeout 3 --max-time 10 --data-urlencode 'username=admin' --data-urlencode ("password={0}" -f $plainPassword) "$base/goform/IADIdentityAuth"
    if ($loginStatus -ne '302') { throw "DINSTAR login failed with HTTP $loginStatus" }
    $ruleStatus = & curl.exe -sS -k -b $jar -o $ruleBody -w '%{http_code}' --connect-timeout 3 --max-time 12 --data-urlencode 'Index=0' --data-urlencode 'Method=0' --data-urlencode 'Encoding=0' --data-urlencode 'Dest=333' --data-urlencode 'Text=MMN' --data-urlencode 'Src=' --data-urlencode 'Key=[*][N]' --data-urlencode 'IsWRSim=1' --data-urlencode 'RmFromLeft=0' --data-urlencode 'AddPrefix=' --data-urlencode 'PortGroup=0' --data-urlencode 'Ok=Save' "$base/goform/HBPhoneNumberRuleAdd"
    if ($ruleStatus -notin @('200','302')) { throw "DINSTAR rule creation failed with HTTP $ruleStatus" }
    $verify = & curl.exe -sS -k -b $jar --connect-timeout 3 --max-time 10 "$base/enHBPhoneNumber.htm"
    $hasRule = ($verify -match '>333<' -and $verify -match '>MMN<' -and $verify -match '\[\*\]\[N\]')
    Write-Output ("RULE_CREATE_HTTP={0}" -f $ruleStatus)
    Write-Output ("RULE_VERIFIED={0}" -f $hasRule)
    if (-not $hasRule) { throw 'Rule was not visible after creation; stop before restarting any port.' }
}
finally {
    if ($ptr -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
    Remove-Variable plainPassword -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $jar,$loginBody,$ruleBody -Force -ErrorAction SilentlyContinue
}
