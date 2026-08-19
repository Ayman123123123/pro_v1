$dirs = Get-ChildItem lib -Directory | Select-Object -ExpandProperty Name
foreach ($dir in $dirs) {
    $file = "lib/$dir/build.gradle.kts"
    if (Test-Path $file) {
        $content = Get-Content "$file" -Raw
        if ($content -match 'id\("signal-') {
            Write-Host $dir + ": uses signal plugin"
        } elseif ($content -match 'id\("com\.android') {
            Write-Host $dir + ": standard android"
        } elseif ($content -match 'id\("java') {
            Write-Host $dir + ": java library"
        } else {
            Write-Host $dir + ": other"
        }
    }
}