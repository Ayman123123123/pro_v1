$ErrorActionPreference = 'SilentlyContinue'
$roots = [ordered]@{
  source_01 = 'C:\Users\hpc01\Pictures\pro_new\RED_Ultimate_V1-main\RED_Ultimate'
  source_02 = 'C:\Users\hpc01\Pictures\pro_new\upload-clean\RED_Ultimate_V1-main\RED_Ultimate'
  source_03 = 'C:\Users\hpc01\Documents\pro_new\RED_Ultimate_V1-main\RED_Ultimate'
  source_04 = 'C:\Users\hpc01\Documents\pro_new\upload-clean\RED_Ultimate_V1-main\RED_Ultimate'
  source_05 = 'C:\Users\hpc01\AndroidStudioProjects\pro\pro\RED_Ultimate_V1-main\RED_Ultimate'
  source_06 = 'C:\Users\hpc01\AndroidStudioProjects\pro\project\pro\RED_Ultimate_V1-main\RED_Ultimate'
  source_07 = 'C:\Users\hpc01\Pictures\pro\RED_Ultimate_V1-main\RED_Ultimate'
  source_08 = 'C:\red'
}
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$out = "C:\Users\hpc01\Pictures\RED_copy_comparison_$stamp"
New-Item -ItemType Directory -Force -Path $out | Out-Null
$rows = New-Object System.Collections.Generic.List[object]
foreach ($name in $roots.Keys) {
  $root = $roots[$name]
  if (!(Test-Path -LiteralPath $root)) { continue }
  Get-ChildItem -LiteralPath $root -File -Recurse -Force -ErrorAction SilentlyContinue |
    Where-Object { $_.FullName -notmatch '\\(node_modules|build|dist|\.gradle|\.android_home|\.git|target|\.idea|\.cache)(\\|$)' } |
    ForEach-Object {
      $rel = $_.FullName.Substring($root.Length).TrimStart('\')
      $h = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
      $rows.Add([pscustomobject]@{Source=$name;RelativePath=$rel;Length=$_.Length;LastWriteTime=$_.LastWriteTime.ToString('o');SHA256=$h})
    }
}
$rows | Export-Csv (Join-Path $out 'file-manifest.csv') -NoTypeInformation -Encoding UTF8
$summary = $rows | Group-Object Source | ForEach-Object { [pscustomobject]@{Source=$_.Name;Files=$_.Count;Bytes=(($_.Group | Measure-Object Length -Sum).Sum)} }
$summary | Export-Csv (Join-Path $out 'summary.csv') -NoTypeInformation -Encoding UTF8
$summary | Format-Table -AutoSize
Write-Output "COMPARISON_ROOT=$out"
