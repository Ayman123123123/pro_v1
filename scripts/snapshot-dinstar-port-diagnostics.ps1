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
    $raw = (@(& curl.exe -sS -k -b $jar --connect-timeout 3 --max-time 10 "$base/WebGetPortInfoAll") -join "`n")
    $items = $raw | ConvertFrom-Json
    $known = @('port','type','network','status','reg','signal','callstate','call_limit','gprs','operator','sms','smsc','number','imsi','iccid')
    $out = foreach ($item in $items) {
        if ($item.port -eq 'Total' -or $null -eq $item.port) { continue }
        $props = [ordered]@{}
        foreach ($name in $known) {
            $value = $item.$name
            if ($name -in @('imsi','iccid','number') -and $value) {
                $text = [string]$value
                $value = if ($text.Length -le 4) { '****' } else { ('*' * ($text.Length - 4)) + $text.Substring($text.Length - 4) }
            }
            $props[$name] = $value
        }
        [pscustomobject]$props
    }
    $snapshotPath = 'C:\Users\hpc01\Pictures\pro_new\DINSTAR_PORT_DIAGNOSTICS_SANITIZED_2026-08-23.json'
    $out | ConvertTo-Json -Depth 3 | Set-Content -LiteralPath $snapshotPath -Encoding UTF8
    Write-Output ("SANITIZED_SNAPSHOT={0}" -f $snapshotPath)
    $out | Format-Table -AutoSize
    Write-Output '=== RAW_FIELD_NAMES ==='
    (($items | Where-Object { $_.port -eq 0 } | Select-Object -First 1).psobject.Properties.Name | Sort-Object) -join ','
}
finally {
    if ($ptr -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
    Remove-Variable plainPassword -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $jar,$loginBody -Force -ErrorAction SilentlyContinue
}
