param(
    [int]$ListenPort = 514,
    [int]$Seconds = 600
)
$ErrorActionPreference = 'Stop'
$logPath = 'C:\Users\hpc01\Pictures\pro_new\DINSTAR_SYSLOG_CAPTURE_2026-08-23.log'
$encoding = [System.Text.UTF8Encoding]::new($false)
$udp = [System.Net.Sockets.UdpClient]::new($ListenPort)
$udp.Client.ReceiveTimeout = 1000
$deadline = (Get-Date).AddSeconds($Seconds)
("# DINSTAR syslog capture start {0:O}; UDP/{1}; duration={2}s" -f (Get-Date),$ListenPort,$Seconds) | Out-File -LiteralPath $logPath -Encoding utf8
try {
    while ((Get-Date) -lt $deadline) {
        try {
            $remote = [System.Net.IPEndPoint]::new([System.Net.IPAddress]::Any, 0)
            $bytes = $udp.Receive([ref]$remote)
            $message = $encoding.GetString($bytes)
            ("{0:O} from {1}:{2} {3}" -f (Get-Date),$remote.Address,$remote.Port,$message.Trim()) | Add-Content -LiteralPath $logPath -Encoding utf8
        }
        catch [System.Net.Sockets.SocketException] {
            if ($_.Exception.SocketErrorCode -ne [System.Net.Sockets.SocketError]::TimedOut) { throw }
        }
    }
}
finally {
    $udp.Dispose()
    ("# DINSTAR syslog capture end {0:O}" -f (Get-Date)) | Add-Content -LiteralPath $logPath -Encoding utf8
}
