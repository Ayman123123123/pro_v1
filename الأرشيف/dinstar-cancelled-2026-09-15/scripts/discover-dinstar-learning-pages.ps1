$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Runtime.InteropServices
$base = 'https://192.168.11.2'
$jar = Join-Path $env:TEMP ('dinstar-cookie-' + [guid]::NewGuid().ToString() + '.txt')
$loginBody = Join-Path $env:TEMP ('dinstar-login-' + [guid]::NewGuid().ToString() + '.txt')
$securePassword = Read-Host 'DINSTAR admin password' -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    $loginStatus = & curl.exe -sS -k -c $jar -o $loginBody -w '%{http_code}' --connect-timeout 3 --max-time 10 --data-urlencode 'username=admin' --data-urlencode ("password={0}" -f $plainPassword) "$base/goform/IADIdentityAuth"
    if ($loginStatus -ne '302') { throw "DINSTAR login failed with HTTP $loginStatus" }
    $pages = @('enFrame.htm','enMain.htm','enMenu.htm','enLeft.htm','enIndex.htm')
    $links = New-Object System.Collections.Generic.HashSet[string]
    foreach ($page in $pages) {
        $body = & curl.exe -sS -k -b $jar --connect-timeout 3 --max-time 8 "$base/$page"
        [regex]::Matches($body, '[A-Za-z0-9_\-]+\.htm') | ForEach-Object { [void]$links.Add($_.Value) }
    }
    Write-Output '=== SIP_RELATED_PAGES ==='
    $links | Where-Object { $_ -match 'sip|account|service|trunk' } | Sort-Object
    Write-Output '=== ALL_DISCOVERED_PAGE_COUNT ==='
    $links.Count
}
finally {
    if ($ptr -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
    Remove-Variable plainPassword -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $jar,$loginBody -Force -ErrorAction SilentlyContinue
}
