$ErrorActionPreference = 'Stop'
$outFile = 'C:\Users\hpc01\Pictures\pro_new\ASTERISK_SIP_STATUS_2026-08-23.txt'
$commands = @(
    'pjsip show endpoints',
    'pjsip show contacts',
    'pjsip show aors',
    'pjsip show identifies',
    'pjsip show endpoint red-webrtc-client'
)
$lines = New-Object System.Collections.Generic.List[string]
foreach ($command in $commands) {
    [void]$lines.Add("=== $command ===")
    $result = @(& docker.exe exec red-pstn-gateway asterisk -rx $command 2>&1)
    $result | ForEach-Object { [void]$lines.Add([string]$_) }
    [void]$lines.Add('')
}
$lines | Set-Content -LiteralPath $outFile -Encoding UTF8
Write-Output ("SIP_STATUS_FILE={0}" -f $outFile)
