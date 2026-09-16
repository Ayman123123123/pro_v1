$ErrorActionPreference = 'Continue'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backupRoot = "C:\Users\hpc01\Pictures\RED_consolidation_backup_$stamp"
New-Item -ItemType Directory -Force -Path $backupRoot | Out-Null
$sources = @(
  'C:\Users\hpc01\Pictures\pro_new\RED_Ultimate_V1-main\RED_Ultimate',
  'C:\Users\hpc01\Pictures\pro_new\upload-clean\RED_Ultimate_V1-main\RED_Ultimate',
  'C:\Users\hpc01\Documents\pro_new\RED_Ultimate_V1-main\RED_Ultimate',
  'C:\Users\hpc01\Documents\pro_new\upload-clean\RED_Ultimate_V1-main\RED_Ultimate',
  'C:\Users\hpc01\AndroidStudioProjects\pro\pro\RED_Ultimate_V1-main\RED_Ultimate',
  'C:\Users\hpc01\AndroidStudioProjects\pro\project\pro\RED_Ultimate_V1-main\RED_Ultimate',
  'C:\Users\hpc01\Pictures\pro\RED_Ultimate_V1-main\RED_Ultimate',
  'C:\red'
)
$index = @()
$i = 0
foreach ($src in $sources) {
  if (Test-Path -LiteralPath $src) {
    $i++
    $name = ('source_{0:00}' -f $i)
    $dst = Join-Path $backupRoot $name
    New-Item -ItemType Directory -Force -Path $dst | Out-Null
    robocopy $src $dst /E /COPY:DAT /DCOPY:DAT /R:1 /W:1 /XJ /XD node_modules build .gradle .android_home .git target dist .idea /NFL /NDL /NP /LOG:(Join-Path $backupRoot "$name-robocopy.log") | Out-Null
    $index += [pscustomobject]@{Name=$name;Source=$src;Destination=$dst;Files=(Get-ChildItem -LiteralPath $src -File -Recurse -ErrorAction SilentlyContinue | Measure-Object).Count}
  }
}
$index | Export-Csv (Join-Path $backupRoot 'backup-index.csv') -NoTypeInformation -Encoding UTF8
Get-FileHash (Join-Path $backupRoot 'backup-index.csv') -Algorithm SHA256 | Format-List
Write-Output "BACKUP_ROOT=$backupRoot"
$index | Format-Table -AutoSize
