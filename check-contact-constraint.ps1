$root='C:\Users\hpc01\RED_Ultimate_UNIFIED'
$out='C:\Users\hpc01\Pictures\pro_new\contact-constraint.txt'
$paths=@((Join-Path $root 'backend-server\src\main\resources'),(Join-Path $root 'backend-server\src\main\kotlin'),(Join-Path $root 'database'),(Join-Path $root 'drizzle'))
$rows=foreach($p in $paths){Get-ChildItem -LiteralPath $p -File -Recurse -ErrorAction SilentlyContinue | Select-String -Pattern 'uq_contact_request_pending|contact_requests|CREATE UNIQUE|partial|ON CONFLICT' -Context 2,4 | ForEach-Object {"$($_.Path)|$($_.LineNumber)|$($_.Line.Trim())"}}
$rows | Set-Content -LiteralPath $out -Encoding UTF8
Write-Output "OUT=$out COUNT=$($rows.Count)"
