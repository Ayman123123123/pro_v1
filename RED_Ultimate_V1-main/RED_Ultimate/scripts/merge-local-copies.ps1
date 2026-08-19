# merge-local-copies.ps1 - Merge ALL local project copies into ONE and push to GitHub
# -----------------------------------------------------------------------------
# ASCII-only version (works on Windows PowerShell 5.1).
# Scans every project folder on this machine, copies every unique/newer file
# into Pictures\pro, commits everything, and pushes to branch "local-full-merge".
#
# SAFETY: pushing is disabled by default. Run with -Push to enable it:
# Run:  powershell -ExecutionPolicy Bypass -File merge-local-copies.ps1 -Push
param(
  [switch]$Push
)
$ErrorActionPreference = "Continue"

$dest = "C:\Users\hpc01\Pictures\pro"
$sources = @(
  "C:\Users\hpc01\AndroidStudioProjects\pro",
  "C:\Users\hpc01\Pictures\pro-arena-security"
)
$skipDirs = @('.git','node_modules','build','dist','.gradle','.idea','.vs','.cache','__pycache__','out','target','.venv','.terraform')

Write-Host "============================================================"
Write-Host "  MERGE ALL LOCAL PROJECT COPIES INTO ONE + PUSH TO GITHUB"
Write-Host "============================================================"

if (-not (Test-Path $dest)) {
  Write-Host "[ERROR] Destination folder missing: $dest"
  exit 1
}
Set-Location $dest

# --- 1) Commit current state (all files + reports) --------------------------
Write-Host ""
Write-Host "[1/4] Committing current state (all files + reports)..."
git config user.name "Ayman" 2>$null
git config user.email "Ayman123123123@users.noreply.github.com" 2>$null
git add -A
git commit -m "Full local state: all files and reports" --allow-empty | Out-Null
Write-Host "   OK - committed"

# --- 2) Merge every other copy (all files, never delete) --------------------
Write-Host ""
Write-Host "[2/4] Merging other local copies..."
$ops = 0
foreach ($s in $sources) {
  if (-not (Test-Path $s)) { Write-Host "   (missing: $s)"; continue }
  Write-Host "   <- $s"
  Get-ChildItem $s -Recurse -File -Force -ErrorAction SilentlyContinue | ForEach-Object {
    $f = $_
    foreach ($d in $skipDirs) { if ($f.FullName -match [regex]::Escape("\$d\")) { return } }
    $rel = $f.FullName.Substring($s.Length).TrimStart('\')
    if ([string]::IsNullOrWhiteSpace($rel)) { return }
    $target = Join-Path $dest $rel
    if (-not (Test-Path $target)) {
      $parent = Split-Path $target -Parent
      if ($parent) { New-Item -ItemType Directory -Force -Path $parent -ErrorAction SilentlyContinue | Out-Null }
      Copy-Item $f.FullName $target -Force -ErrorAction SilentlyContinue
      $script:ops++
      Write-Host "     + $rel" -ForegroundColor Green
    } elseif ((Get-Item $target).LastWriteTime -lt $f.LastWriteTime) {
      Copy-Item $f.FullName $target -Force -ErrorAction SilentlyContinue
      $script:ops++
      Write-Host "     ^ $rel" -ForegroundColor Yellow
    }
  }
}
Write-Host "   (merged $ops files/updates)"

# --- 3) Commit merged files ------------------------------------------------
Write-Host ""
Write-Host "[3/4] Committing merged files..."
git add -A
git commit -m "Merged all local copies into one unified project" --allow-empty | Out-Null
Write-Host "   OK - committed"

# --- 4) Push to GitHub (OPT-IN: requires -Push) ------------------------------
if (-not $Push) {
  Write-Host ""
  Write-Host "[4/4] SKIPPED - pushing to GitHub is disabled by default."
  Write-Host "       Re-run with -Push to push branch local-full-merge."
  exit 0
}
Write-Host ""
Write-Host "[4/4] Pushing to GitHub (new branch: local-full-merge)..."
git push origin HEAD:local-full-merge 2>&1 | ForEach-Object { Write-Host "   $_" }
if ($LASTEXITCODE -eq 0) {
  Write-Host ""
  Write-Host "============================================================"
  Write-Host "  SUCCESS - pushed to branch: local-full-merge"
  Write-Host "  The AI will now pull it and merge into the main project."
  Write-Host "============================================================"
} else {
  Write-Host ""
  Write-Host "[WARNING] Push failed. Login and retry:"
  Write-Host "   gh auth login"
  Write-Host "   powershell -ExecutionPolicy Bypass -File merge-local-copies.ps1"
}
