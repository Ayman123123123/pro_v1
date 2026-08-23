$ErrorActionPreference = 'Stop'
$base = 'https://192.168.11.2'
$envFile = 'C:\Users\hpc01\Pictures\pro_new\RED_Ultimate_V1-main\RED_Ultimate\.env'
$root = 'C:\Users\hpc01\Pictures\pro_new'
$targets = @(
    @{ Page = 'enRouteIP2PSTNList.htm'; File = 'DINSTAR_IP_TO_TEL_ROUTING_2026-08-23.html' },
    @{ Page = 'enPortGroup.htm'; File = 'DINSTAR_PORT_GROUPS_2026-08-23.html' },
    @{ Page = 'enPortConfig.htm'; File = 'DINSTAR_PORT_CONFIG_2026-08-23.html' }
)
$jar = Join-Path $env:TEMP ('dinstar-cookie-' + [guid]::NewGuid().ToString() + '.txt')
$loginBody = Join-Path $env:TEMP ('dinstar-login-' + [guid]::NewGuid().ToString() + '.txt')

try {
    $passwordLine = Get-Content -LiteralPath $envFile | Where-Object { $_ -match '^DINSTAR_PASSWORD=' } | Select-Object -First 1
    if (-not $passwordLine) { throw 'DINSTAR password is unavailable in the runtime environment.' }
    $password = $passwordLine.Substring('DINSTAR_PASSWORD='.Length)
    $loginStatus = & curl.exe -sS -k -c $jar -o $loginBody -w '%{http_code}' --connect-timeout 3 --max-time 10 --retry 0 --data-urlencode 'username=admin' --data-urlencode ("password={0}" -f $password) "$base/goform/IADIdentityAuth"
    if ($loginStatus -ne '302') { throw "DINSTAR login failed with HTTP $loginStatus" }
    foreach ($target in $targets) {
        $out = Join-Path $root $target.File
        $status = & curl.exe -sS -k -b $jar -o $out -w '%{http_code}' --connect-timeout 3 --max-time 10 --retry 0 "$base/$($target.Page)"
        if ($status -ne '200') { throw "DINSTAR routing page $($target.Page) returned HTTP $status" }
        Write-Output ("READONLY_PAGE={0};HTTP={1};FILE={2}" -f $target.Page,$status,$out)
    }
}
finally {
    Remove-Variable password -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $jar,$loginBody -Force -ErrorAction SilentlyContinue
}
