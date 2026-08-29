$ErrorActionPreference = 'Stop'
$source = 'C:\Users\hpc01\Pictures\pro_new\RED_Ultimate_V1-main\RED_Ultimate'
$unified = 'C:\Users\hpc01\RED_Ultimate_UNIFIED'
if (!(Test-Path -LiteralPath $source)) { throw "Source missing: $source" }
New-Item -ItemType Directory -Force -Path $unified | Out-Null
$log = Join-Path $unified 'unification-robocopy.log'
& robocopy $source $unified /E /COPY:DAT /DCOPY:DAT /R:1 /W:1 /XJ /XD node_modules build .gradle .android_home target dist .idea /NFL /NDL /NP "/LOG:$log" | Out-Null
if ($LASTEXITCODE -gt 7) { throw "Robocopy failed with exit code $LASTEXITCODE" }
$meta = Join-Path $unified '_consolidation'
New-Item -ItemType Directory -Force -Path $meta | Out-Null
@{
  Created = (Get-Date).ToString('o')
  BaseSource = $source
  GitHubSource = 'C:\Users\hpc01\Pictures\pro_new\github-git-partial-clone\RED_Ultimate_V1-main\RED_Ultimate'
  GitHubCommit = '4a54de714b88191691aad29ff429ebb6b8171a77'
  GitHubBranch = 'feature/uc2000-ve-sms-browser-audit'
  BackupRoot = (Get-ChildItem 'C:\Users\hpc01\Pictures' -Directory -Filter 'RED_consolidation_backup_*' | Sort-Object LastWriteTime -Descending | Select-Object -First 1 -ExpandProperty FullName)
} | ConvertTo-Json | Set-Content (Join-Path $meta 'provenance.json') -Encoding UTF8
@'
# RED Unified Local Root

This directory was created as a non-destructive consolidation target. The verified local Pictures/pro_new tree was used as the code base because it passed the latest Backend, Admin Dashboard, and Android checks. GitHub was cloned separately for comparison at the commit recorded in `_consolidation/provenance.json`.

Original copies were not deleted or overwritten. The `.env` file remains local-only and must not be committed or published.
'@ | Set-Content (Join-Path $meta 'README.md') -Encoding UTF8
Write-Output "UNIFIED_ROOT=$unified"
Get-ChildItem -LiteralPath $unified -Name | Select-Object -First 30
