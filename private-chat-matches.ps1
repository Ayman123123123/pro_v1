$root='C:\Users\hpc01\RED_Ultimate_UNIFIED'
$out='C:\Users\hpc01\Pictures\pro_new\private-chat-matches.txt'
$patterns=@('private','conversation','contact','userId','addFriend','createConversation','/users','/contacts','INTERNAL_ERROR','search')
$dirs=@((Join-Path $root 'red-app\src\main'),(Join-Path $root 'backend-server\src\main'),(Join-Path $root 'backend-server\src\test'))
$rows=foreach($dir in $dirs){Get-ChildItem -LiteralPath $dir -File -Recurse -ErrorAction SilentlyContinue | Where-Object {$_.Extension -in @('.kt','.java','.ts','.tsx','.js','.yml','.yaml','.json','.sql')} | Select-String -Pattern $patterns -Context 0,0 | ForEach-Object {"$($_.Path)|$($_.LineNumber)|$($_.Line.Trim())"}}
$rows | Set-Content -LiteralPath $out -Encoding UTF8
Write-Output "MATCH_FILE=$out COUNT=$($rows.Count)"
