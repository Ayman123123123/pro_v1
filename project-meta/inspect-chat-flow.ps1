$root='C:\Users\hpc01\RED_Ultimate_UNIFIED'
$out='C:\Users\hpc01\Pictures\pro_new\chat-flow-inspect.txt'
$lines=New-Object System.Collections.Generic.List[string]
$lines.Add('=== CANDIDATE FILES ===')
Get-ChildItem -LiteralPath (Join-Path $root 'red-app\src\main\java') -File -Recurse -ErrorAction SilentlyContinue | Where-Object {$_.Name -match 'Chat|Contact|Conversation|Friend|User|Search|Auth'} | ForEach-Object {$lines.Add($_.FullName)}
$lines.Add('=== ANDROID API REFERENCES ===')
Get-ChildItem -LiteralPath (Join-Path $root 'red-app\src\main\java') -File -Recurse -ErrorAction SilentlyContinue | Where-Object {$_.Extension -eq '.kt'} | Select-String -Pattern 'INTERNAL_ERROR|/users|/contacts|conversation|friend|search' -Context 1,2 | ForEach-Object {$lines.Add("$($_.Path)|$($_.LineNumber)|$($_.Line.Trim())")}
$lines.Add('=== BACKEND API REFERENCES ===')
Get-ChildItem -LiteralPath (Join-Path $root 'backend-server\src\main\kotlin') -File -Recurse -ErrorAction SilentlyContinue | Where-Object {$_.Extension -eq '.kt'} | Select-String -Pattern 'INTERNAL_ERROR|@.*Mapping|/users|/contacts|conversation|friend|search' -Context 1,2 | ForEach-Object {$lines.Add("$($_.Path)|$($_.LineNumber)|$($_.Line.Trim())")}
$lines | Set-Content -LiteralPath $out -Encoding UTF8
Write-Output "OUT=$out LINES=$($lines.Count)"
