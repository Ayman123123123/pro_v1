$ErrorActionPreference = 'Stop'
$gateway = '192.168.11.2'
$outFile = 'C:\Users\hpc01\Pictures\pro_new\DINSTAR_SIP_OPTIONS_PORT_PROBE_2026-08-23.json'
$results = foreach ($index in 0..7) {
    $port = 5060 + $index
    $udp = [System.Net.Sockets.UdpClient]::new(0)
    $udp.Client.ReceiveTimeout = 2500
    try {
        $localPort = ([System.Net.IPEndPoint]$udp.Client.LocalEndPoint).Port
        $branch = 'z9hG4bK' + [guid]::NewGuid().ToString('N').Substring(0,12)
        $callId = [guid]::NewGuid().ToString('N') + '@red-sip-probe'
        $request = @(
            "OPTIONS sip:probe@$gateway`:$port SIP/2.0"
            "Via: SIP/2.0/UDP 192.168.11.20:$localPort;branch=$branch;rport"
            'Max-Forwards: 70'
            "To: <sip:probe@$gateway`:$port>"
            'From: <sip:red-sip-probe@192.168.11.20>;tag=probe'
            "Call-ID: $callId"
            'CSeq: 1 OPTIONS'
            "Contact: <sip:red-sip-probe@192.168.11.20:$localPort>"
            'User-Agent: RED-DINSTAR-SafeProbe/1.0'
            'Content-Length: 0'
            ''
            ''
        ) -join "`r`n"
        $bytes = [System.Text.Encoding]::ASCII.GetBytes($request)
        [void]$udp.Send($bytes, $bytes.Length, $gateway, $port)
        $remote = [System.Net.IPEndPoint]::new([System.Net.IPAddress]::Any, 0)
        $reply = [System.Text.Encoding]::ASCII.GetString($udp.Receive([ref]$remote))
        $statusLine = ($reply -split "`r?`n" | Select-Object -First 1)
        [pscustomobject]@{ portIndex=$index; sipPort=$port; responded=$true; remote="$($remote.Address):$($remote.Port)"; statusLine=$statusLine }
    }
    catch [System.Net.Sockets.SocketException] {
        [pscustomobject]@{ portIndex=$index; sipPort=$port; responded=$false; remote=$null; statusLine='timeout' }
    }
    finally {
        $udp.Dispose()
    }
}
$results | ConvertTo-Json | Out-File -LiteralPath $outFile -Encoding utf8
$results | Format-Table -AutoSize
Write-Output "RESULT_FILE=$outFile"
