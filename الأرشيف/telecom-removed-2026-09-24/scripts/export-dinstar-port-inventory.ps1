$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Runtime.InteropServices
$base = 'https://192.168.11.2'
$project = 'C:\Users\hpc01\Pictures\pro_new'
$outFile = Join-Path $project 'DINSTAR_PORT_INVENTORY_2026-08-23.json'
$targetNumbers = @('712064924','712065754','712065805','712065242','712065388','712065586','712065191','712068639')
$jar = Join-Path $env:TEMP ('dinstar-cookie-' + [guid]::NewGuid().ToString() + '.txt')
$loginBody = Join-Path $env:TEMP ('dinstar-login-' + [guid]::NewGuid().ToString() + '.txt')
$portsBody = Join-Path $env:TEMP ('dinstar-ports-' + [guid]::NewGuid().ToString() + '.json')
$securePassword = Read-Host 'DINSTAR admin password' -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
function Get-Digits([object]$value) { if ($null -eq $value) { return '' }; return ([string]$value -replace '\D','') }
function Mask([object]$value) { $d = Get-Digits $value; if ($d.Length -le 4) { return $d }; return ('*' * ($d.Length - 4)) + $d.Substring($d.Length - 4) }
function Get-FirstValue($item, [string[]]$names) { foreach ($n in $names) { $p = $item.PSObject.Properties[$n]; if ($null -ne $p -and $null -ne $p.Value -and [string]$p.Value -ne '') { return $p.Value } }; return $null }
try {
    $plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    $loginStatus = & curl.exe -sS -k -c $jar -o $loginBody -w '%{http_code}' --connect-timeout 3 --max-time 10 --data-urlencode 'username=admin' --data-urlencode ("password={0}" -f $plainPassword) "$base/goform/IADIdentityAuth"
    if ($loginStatus -ne '302') { throw "DINSTAR login failed with HTTP $loginStatus" }
    $portsStatus = & curl.exe -sS -k -b $jar -o $portsBody -w '%{http_code}' --connect-timeout 3 --max-time 10 -H 'Accept: application/json' "$base/WebGetPortInfoAll"
    if ($portsStatus -ne '200') { throw "DINSTAR port inventory failed with HTTP $portsStatus" }
    $rawPorts = @(Get-Content -LiteralPath $portsBody -Raw | ConvertFrom-Json) | Where-Object { $_.port -ne 'Total' }
    $fieldNames = @($rawPorts | ForEach-Object { $_.PSObject.Properties.Name } | Sort-Object -Unique)
    $rows = foreach ($raw in $rawPorts) {
        $possibleNumbers = @('number','phone','phone_number','msisdn','sim_number','simnum') | ForEach-Object { Get-Digits $raw.PSObject.Properties[$_].Value } | Where-Object { $_ }
        $matched = $possibleNumbers | Where-Object { $targetNumbers -contains $_ } | Select-Object -First 1
        [PSCustomObject]@{
            port = [int]$raw.port
            radioType = Get-FirstValue $raw @('type','radio','module_type')
            network = Get-FirstValue $raw @('network','reg','registration','status')
            callState = Get-FirstValue $raw @('call_status','callstate','callState')
            signal = Get-FirstValue $raw @('signal','csq','rssi')
            operator = Get-FirstValue $raw @('operator','operator_name','plmn')
            matchedRequestedNumber = $matched
            reportedNumberMasked = Mask (Get-FirstValue $raw @('number','phone','phone_number','msisdn','sim_number','simnum'))
            imsiMasked = Mask (Get-FirstValue $raw @('imsi'))
            iccidMasked = Mask (Get-FirstValue $raw @('iccid','simid'))
            imeiMasked = Mask (Get-FirstValue $raw @('imei'))
        }
    }
    $report = [PSCustomObject]@{
        generatedAt = (Get-Date).ToUniversalTime().ToString('o')
        gateway = '192.168.11.2'
        source = 'WebGetPortInfoAll'
        rawFieldsDetected = $fieldNames
        ports = $rows
        requestedNumbersUnmatched = @($targetNumbers | Where-Object { $_ -notin $rows.matchedRequestedNumber })
    }
    $report | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $outFile -Encoding UTF8
    Write-Output ("PORT_COUNT={0}" -f $rows.Count)
    Write-Output ("SNAPSHOT={0}" -f $outFile)
    $rows | Format-Table -AutoSize
}
finally {
    if ($ptr -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
    Remove-Variable plainPassword -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $jar,$loginBody,$portsBody -Force -ErrorAction SilentlyContinue
}
