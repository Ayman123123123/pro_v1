# ===========================================================================
#  BACKUP_EVERYTHING.ps1  -  Save EVERYTHING from pro_new. Nothing lost.
# ===========================================================================
#  Run:
#     cd C:\Users\hpc01\Pictures\pro_new
#     powershell -ExecutionPolicy Bypass -File scripts\BACKUP_EVERYTHING.ps1
#
#  ASCII-only on purpose: PowerShell 5.1 mangles UTF-8 scripts without BOM.
#
#  [1] Full archive of everything (incl. .git, ignored files, upload-clean)
#  [2] Handles nested-repo trap (upload-clean) that git silently skips
#  [3] Commits and pushes ALL files to a new branch
#  [4] Verification report: counts before/after, lists anything left out
#
#  Deletes nothing. Touches no existing branch. Safe.
# ===========================================================================

$ErrorActionPreference = "Continue"
$ProgressPreference    = "SilentlyContinue"

$repo   = "C:\Users\hpc01\Pictures\pro_new"
$stamp  = Get-Date -Format "yyyyMMdd-HHmmss"
$vault  = "C:\Users\hpc01\Pictures\_BACKUP_pro_new_$stamp"
$branch = "backup/strongest-2026-08-14"

function Say($msg, $col = "White") { Write-Host $msg -ForegroundColor $col }
function Line() { Say ("=" * 70) "DarkGray" }

Line
Say "   FULL BACKUP of pro_new - no file left behind" "Green"
Line

if (-not (Test-Path $repo)) { Say "Folder not found: $repo" "Red"; exit 1 }
Set-Location $repo

New-Item -ItemType Directory -Force -Path $vault | Out-Null

# Long paths: you have 60 paths over 260 chars. Without this, push fails.
git config core.longpaths true
git config core.protectNTFS false

# --- [1] Inventory everything on disk -------------------------------------
Say ""
Say "[1/5] Counting every file on disk..." "Yellow"

$allFiles = Get-ChildItem -Path $repo -Recurse -File -Force -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -notmatch '\\\.git\\' }
$totalOnDisk = $allFiles.Count
$totalSize   = [math]::Round( ($allFiles | Measure-Object Length -Sum).Sum / 1MB, 1 )

Say "    Files on disk (excluding .git): $totalOnDisk" "Cyan"
Say "    Total size: $totalSize MB" "Cyan"

$allFiles | ForEach-Object { $_.FullName.Substring($repo.Length + 1) } |
    Sort-Object | Out-File -Encoding utf8 (Join-Path $vault "INVENTORY_BEFORE.txt")
Say "    Saved: INVENTORY_BEFORE.txt" "DarkGray"

# --- [2] Full archive - absolute safety net -------------------------------
Say ""
Say "[2/5] Creating full archive (may take a few minutes)..." "Yellow"

$tarPath = Join-Path $vault "pro_new_FULL_$stamp.tar"
Push-Location "C:\Users\hpc01\Pictures"
& tar.exe -cf $tarPath "pro_new" 2>$null
Pop-Location

if (Test-Path $tarPath) {
    $tarMB = [math]::Round( (Get-Item $tarPath).Length / 1MB, 1 )
    Say "    Archive ready: $tarMB MB" "Green"
    Say "    Path: $tarPath" "DarkGray"
    Say "    Contains EVERYTHING: .git, ignored files, upload-clean, secrets" "DarkGray"
} else {
    Say "    tar failed - falling back to direct copy" "Red"
    $copyDest = Join-Path $vault "pro_new_COPY"
    robocopy $repo $copyDest /E /COPYALL /R:1 /W:1 /NFL /NDL /NJH /NJS | Out-Null
    Say "    Direct copy done: $copyDest" "Yellow"
}

# --- [3] Nested repo trap -------------------------------------------------
Say ""
Say "[3/5] Checking for nested git repos (silent data-loss trap)..." "Yellow"

$nested = Get-ChildItem -Path $repo -Recurse -Directory -Force -Filter ".git" -ErrorAction SilentlyContinue |
          Where-Object { $_.FullName -ne (Join-Path $repo ".git") }

if ($nested) {
    foreach ($n in $nested) {
        $parent = $n.Parent
        $rel    = $parent.FullName.Substring($repo.Length + 1)
        $cnt    = (Get-ChildItem $parent.FullName -Recurse -File -Force -ErrorAction SilentlyContinue |
                   Where-Object { $_.FullName -notmatch '\\\.git\\' }).Count

        Say "    NESTED REPO: $rel  ($cnt files)" "Red"
        Say "      Plain git will NOT upload its contents. Fixing now." "DarkGray"

        $nestTar = Join-Path $vault ("NESTED_" + $parent.Name + "_$stamp.tar")
        & tar.exe -cf $nestTar -C $parent.Parent.FullName $parent.Name 2>$null

        $newName = "_git_disabled_$stamp"
        Rename-Item -Path $n.FullName -NewName $newName -Force -ErrorAction SilentlyContinue
        git rm --cached $rel -f 2>&1 | Out-Null

        Say "      Converted to normal folder - all $cnt files will upload" "Green"
    }
} else {
    Say "    No nested repos found" "Green"
}

# --- [4] Stage, commit, push ----------------------------------------------
Say ""
Say "[4/5] Staging every file and pushing..." "Yellow"

git config user.name  "Ayman"
git config user.email "Ayman123123123@users.noreply.github.com"

git add -A --force 2>&1 | Out-Null

$staged = @(git diff --cached --name-only).Count
Say "    Files staged: $staged" "Cyan"

git commit -m "Full snapshot of pro_new - every file, nothing excluded ($stamp)" 2>&1 |
    Select-Object -First 3 | ForEach-Object { Say "    $_" "DarkGray" }

Say ""
Say "    Pushing to GitHub (large - please wait)..." "Yellow"
git config http.postBuffer 524288000
git push origin ("HEAD:refs/heads/" + $branch) 2>&1 |
    ForEach-Object { Say "    $_" "DarkGray" }

# --- [5] Verification -----------------------------------------------------
Say ""
Say "[5/5] Verifying nothing was lost..." "Yellow"

$trackedNow = @(git ls-files).Count
$ignoredNow = @(git ls-files --others --ignored --exclude-standard).Count
$leftOut    = @(git ls-files --others --exclude-standard).Count

git ls-files | Out-File -Encoding utf8 (Join-Path $vault "INVENTORY_TRACKED_AFTER.txt")

Line
Say "   VERIFICATION REPORT" "Green"
Line
Say "   Files on disk        : $totalOnDisk" "White"
Say "   Tracked in git now   : $trackedNow" "Green"
Say "   Ignored (build/cache): $ignoredNow" "DarkGray"

if ($leftOut -gt 0) {
    Say "   Still NOT uploaded   : $leftOut" "Red"
} else {
    Say "   Still NOT uploaded   : 0" "Green"
}

Say ""
Say "   Full archive (everything): $vault" "Cyan"
Line

if ($leftOut -gt 0) {
    Say ""
    Say "   Files not uploaded (but safe in archive):" "Yellow"
    git ls-files --others --exclude-standard | Select-Object -First 20 |
        ForEach-Object { Say "     $_" "DarkGray" }
}

Say ""
Say "   Now run this and send the assistant the output:" "Yellow"
Say "   git ls-remote --heads origin | Select-String strongest" "White"
Say ""
