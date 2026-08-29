$root='C:\Users\hpc01\RED_Ultimate_UNIFIED'
$patterns=@('192\.168\.0\.181','لم يعثر','الخادم الآمن','إعادة اكتشاف الخادم','تحديد عنوان الخادم','safe server','manual server')
Get-ChildItem -LiteralPath $root -File -Recurse -ErrorAction SilentlyContinue | Where-Object {$_.FullName -notmatch '\\(build|\.gradle|node_modules)\\' -and $_.Extension -in @('.kt','.java','.xml','.json','.properties','.gradle','.kts','.tsx','.ts')} | Select-String -Pattern $patterns -Context 3,5 | ForEach-Object {"$($_.Path):$($_.LineNumber): $($_.Line.Trim())"}
