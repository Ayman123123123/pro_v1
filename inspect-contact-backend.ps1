$root='C:\Users\hpc01\RED_Ultimate_UNIFIED\backend-server\src\main\kotlin'
$out='C:\Users\hpc01\Pictures\pro_new\contact-backend-inspect.txt'
$files=Get-ChildItem -LiteralPath $root -File -Recurse -ErrorAction SilentlyContinue | Where-Object {$_.Name -match 'Contact|Directory|Friend|Conversation|User' -and $_.Extension -eq '.kt'}
$lines=New-Object System.Collections.Generic.List[string]
foreach($f in $files){$lines.Add("=== $($f.FullName) ==="); Get-Content $f.FullName | Select-String -Pattern '@.*Mapping|/api/contacts|/api/directory|INTERNAL_ERROR|create|request|friend|conversation|SQLException|DataIntegrity' -Context 2,5 | ForEach-Object {$lines.Add("$($_.LineNumber)|$($_.Line.Trim())")}}
$lines | Set-Content -LiteralPath $out -Encoding UTF8
Write-Output "OUT=$out FILES=$($files.Count) LINES=$($lines.Count)"
