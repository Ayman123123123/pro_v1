$ErrorActionPreference = 'Continue'
$hostIp = '192.168.11.2'
$ports = @(80, 443, 5060, 5061, 5062, 5063, 5064, 5065, 5066, 5067)

function Test-TcpPort {
    param([string]$HostIp, [int]$Port, [int]$TimeoutMs = 1800)
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $task = $client.ConnectAsync($HostIp, $Port)
        $ok = $task.Wait($TimeoutMs)
        [pscustomobject]@{ port = $Port; tcp_open = ($ok -and $client.Connected) }
    } catch {
        [pscustomobject]@{ port = $Port; tcp_open = $false }
    } finally {
        $client.Dispose()
    }
}

$pingOutput = & ping.exe -n 2 -w 1000 $hostIp 2>&1
$portsResult = foreach ($port in $ports) { Test-TcpPort -HostIp $hostIp -Port $port }
$route = Get-NetRoute -DestinationPrefix '192.168.11.0/24' -ErrorAction SilentlyContinue |
    Select-Object ifIndex, InterfaceAlias, NextHop, RouteMetric, State
$arp = & arp.exe -a $hostIp 2>&1

[pscustomobject]@{
    timestamp_utc = (Get-Date).ToUniversalTime().ToString('o')
    target = $hostIp
    ping = ($pingOutput | Out-String).Trim()
    tcp_ports = $portsResult
    route = $route
    arp = ($arp | Out-String).Trim()
} | ConvertTo-Json -Depth 6
