$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Runtime.InteropServices
$base = 'https://192.168.11.2'
$sample = 'Your number is 712064924'
$keys = @('[N]','[*][N]','Your number is[N]','Your number is [N]')
$jar = Join-Path $env:TEMP ('dinstar-cookie-' + [guid]::NewGuid().ToString() + '.txt')
$loginBody = Join-Path $env:TEMP ('dinstar-login-' + [guid]::NewGuid().ToString() + '.txt')
$securePassword = Read-Host 'DINSTAR admin password' -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    $loginStatus = & curl.exe -sS -k -c $jar -o $loginBody -w '%{http_code}' --connect-timeout 3 --max-time 10 --data-urlencode 'username=admin' --data-urlencode ("password={0}" -f $plainPassword) "$base/goform/IADIdentityAuth"
    if ($loginStatus -ne '302') { throw "DINSTAR login failed with HTTP $loginStatus" }
    foreach ($key in $keys) {
        $null = & curl.exe -sS -k -b $jar --connect-timeout 3 --max-time 10 --data-urlencode ("PhoneSMS={0}" -f $sample) --data-urlencode ("PhoneKey={0}" -f $key) "$base/goform/EiaHBPhoneNumberTest"
        $result = & curl.exe -sS -k -b $jar --connect-timeout 3 --max-time 10 "$base/enHBPhoneNumberTestResult.htm"
        $text = ((@($result) -join "`n") -replace '<[^>]+>', ' ' -replace '\s+', ' ').Trim()
        Write-Output ("KEY={0}`nRESULT={1}`n" -f $key,$text)
    }
}
finally {
    if ($ptr -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
    Remove-Variable plainPassword -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $jar,$loginBody -Force -ErrorAction SilentlyContinue
}
