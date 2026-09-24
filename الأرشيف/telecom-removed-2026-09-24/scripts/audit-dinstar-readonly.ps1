$ErrorActionPreference = 'Stop'
$envFile = 'C:\Users\hpc01\Pictures\pro_new\RED_Ultimate_V1-main\RED_Ultimate\.env'
$target = 'https://192.168.11.2/api/get_port_info?port=0&info_type=slot,callstate,signal,gprs'
$values = @{}
foreach ($line in Get-Content -LiteralPath $envFile) {
    if ($line -match '^\s*(DINSTAR_USERNAME|DINSTAR_PASSWORD)\s*=\s*(.+?)\s*$') {
        $values[$Matches[1]] = $Matches[2].Trim().Trim('"').Trim("'")
    }
}
if ([string]::IsNullOrWhiteSpace($values['DINSTAR_USERNAME']) -or [string]::IsNullOrWhiteSpace($values['DINSTAR_PASSWORD'])) {
    Write-Output 'CONFIG_CREDENTIALS_PRESENT=NO'
    exit 2
}
Write-Output 'CONFIG_CREDENTIALS_PRESENT=YES'
$response = & curl.exe -k --anyauth -u ("{0}:{1}" -f $values['DINSTAR_USERNAME'], $values['DINSTAR_PASSWORD']) --connect-timeout 3 --max-time 10 -H 'Accept: application/json' -w "`nHTTP=%{http_code}`n" $target
$response | Where-Object { $_ -notmatch 'DINSTAR_PASSWORD|DINSTAR_USERNAME' }
