$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Runtime.InteropServices
$target = 'https://192.168.11.2/api/get_port_info?port=0&info_type=slot,callstate,signal,gprs'
$securePassword = Read-Host 'DINSTAR admin password' -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    $response = & curl.exe -sS -k --anyauth -u ("admin:{0}" -f $plainPassword) --connect-timeout 3 --max-time 10 -H 'Accept: application/json' -w "`nHTTP=%{http_code}`n" $target
    $response
}
finally {
    if ($ptr -ne [IntPtr]::Zero) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
    Remove-Variable plainPassword -ErrorAction SilentlyContinue
}
