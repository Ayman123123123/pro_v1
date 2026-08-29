$root='C:\Users\hpc01\RED_Ultimate_UNIFIED'
$patterns=@('أشخاص يونس','محادثة','إضافة','Add','private','conversation','contact','userId','61813','INTERNAL_ERROR','searchUsers','addFriend','createConversation')
$dirs=@((Join-Path $root 'red-app\src\main'),(Join-Path $root 'backend-server\src\main'),(Join-Path $root 'backend-server\src\test'))
foreach($dir in $dirs){Get-ChildItem -LiteralPath $dir -File -Recurse -ErrorAction SilentlyContinue | Where-Object {$_.Extension -in @('.kt','.java','.ts','.tsx','.js','.yml','.yaml','.json','.sql')} | Select-String -Pattern $patterns -Context 2,4 | ForEach-Object {"$($_.Path):$($_.LineNumber): $($_.Line.Trim())"}}
