$root='C:\Users\hpc01\RED_Ultimate_UNIFIED\red-app'
Write-Output '=== BUILD CONFIG ==='
Get-Content (Join-Path $root 'build.gradle.kts') -TotalCount 45
Write-Output '=== URL REFERENCES ==='
Get-ChildItem -LiteralPath (Join-Path $root 'src\main') -File -Recurse -ErrorAction SilentlyContinue | Where-Object {$_.Extension -in @('.kt','.java','.xml','.json','.properties')} | Select-String -Pattern 'RED_SERVER_URL|BuildConfig|192\.168\.|8088|serverUrl|baseUrl|health|safe' -Context 2,4 | ForEach-Object {"$($_.Path):$($_.LineNumber): $($_.Line.Trim())"}
