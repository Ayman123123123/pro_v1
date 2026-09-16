$root='C:\Users\hpc01\RED_Ultimate_UNIFIED'
$out='C:\Users\hpc01\Pictures\pro_new\call-flow-inspect.txt'
$patterns=@('CallSignaling','CallDelivery','FCM','Firebase','WebSocket','offer','answer','ice','incoming','call','push','targetUserId','notify')
$dirs=@((Join-Path $root 'red-app\src\main\java'),(Join-Path $root 'backend-server\src\main\kotlin'),(Join-Path $root 'backend-server\src\test\kotlin'))
$lines=New-Object System.Collections.Generic.List[string]
foreach($dir in $dirs){Get-ChildItem -LiteralPath $dir -File -Recurse -ErrorAction SilentlyContinue | Where-Object {$_.Extension -in @('.kt','.java')} | Where-Object {$_.FullName -match 'call|Call|notification|Notification|push|Push|signal|Signal|fcm|Fcm|websocket|WebSocket'} | ForEach-Object {$lines.Add("===FILE $($_.FullName)==="); Select-String -LiteralPath $_.FullName -Pattern $patterns -Context 1,3 | ForEach-Object {$lines.Add("$($_.LineNumber)|$($_.Line.Trim())")}}}
$lines | Set-Content -LiteralPath $out -Encoding UTF8
Write-Output "OUT=$out LINES=$($lines.Count)"
