$ErrorActionPreference = 'Stop'
$name = 'DINSTAR Temporary Syslog Capture UDP 514'
& netsh.exe advfirewall firewall delete rule name="$name" | Out-Null
$addOutput = & netsh.exe advfirewall firewall add rule name="$name" dir=in action=allow protocol=UDP localport=514 remoteip=192.168.11.2 profile=any
if ($LASTEXITCODE -ne 0) { throw "Failed to add temporary syslog firewall rule: $addOutput" }
& netsh.exe advfirewall firewall show rule name="$name" verbose
