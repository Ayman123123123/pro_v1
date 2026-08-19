$dirs = Get-ChildItem feature -Directory | Select-Object -ExpandProperty Name
foreach ($dir in $dirs) {
    $file = "feature/$dir/build.gradle.kts"
    if (Test-Path $file) {
        $content = Get-Content "$file" -Raw
        if ($content -match 'id\("signal-') {
            Write-Host $dir + ": uses signal plugin"
        } elseif ($content -match 'id\("com\.android') {
            Write-Host $dir + ": standard android"
        } else {
            Write-Host $dir + ": other"
        }
    }
}