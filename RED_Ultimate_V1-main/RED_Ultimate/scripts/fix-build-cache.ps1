# ════════════════════════════════════════════════════════════════════════
#  RED Ultimate — Build Cache Fixer
#  Forces a complete rebuild with no cache for backend
#  Use this if Docker Compose build fails with stale Gradle errors
# ════════════════════════════════════════════════════════════════════════

param(
    [string]$ServerIp = "192.168.137.19",
    [switch]$BuildAndroid = $true,
    [switch]$NoCache = $true
)

$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)
$ProjectRoot = Get-Location

Write-Host "═══════════════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  RED Ultimate — Build Cache Fixer" -ForegroundColor Cyan
Write-Host "═══════════════════════════════════════════════════════════════════════" -ForegroundColor Cyan

# Step 1: Stop running containers
Write-Host "`n[1/6] Stopping running containers..." -ForegroundColor Yellow
docker compose down 2>&1 | Out-Null

# Step 2: Remove old images
Write-Host "[2/6] Removing stale images..." -ForegroundColor Yellow
$images = @(
    "red-sovereign-backend",
    "red-sovereign-admin-panel",
    "red-sovereign-pstn-gateway",
    "red-sovereign-media-sfu"
)
foreach ($img in $images) {
    $existing = docker images -q $img
    if ($existing) {
        docker rmi -f $img 2>&1 | Out-Null
        Write-Host "  Removed: $img" -ForegroundColor DarkYellow
    }
}

# Step 3: Clear Gradle build cache
Write-Host "[3/6] Clearing Gradle build cache..." -ForegroundColor Yellow
$gradleCacheDirs = @(
    "$env:USERPROFILE\.gradle\caches\build-cache-1",
    "$env:USERPROFILE\.gradle\caches\kotlin-build",
    "$ProjectRoot\backend-server\build",
    "$ProjectRoot\backend-server\.gradle"
)
foreach ($dir in $gradleCacheDirs) {
    if (Test-Path $dir) {
        Remove-Item -Recurse -Force $dir -ErrorAction SilentlyContinue
        Write-Host "  Cleared: $dir" -ForegroundColor DarkYellow
    }
}

# Step 4: Prune Docker build cache
Write-Host "[4/6] Pruning Docker build cache..." -ForegroundColor Yellow
if ($NoCache) {
    docker builder prune -f 2>&1 | Out-Null
    Write-Host "  Docker build cache pruned" -ForegroundColor DarkYellow
}

# Step 5: Remove Docker volumes (if needed for full reset)
Write-Host "[5/6] Verifying files are up-to-date..." -ForegroundColor Yellow
$gitStatus = git status --short backend-server/ 2>&1
if ($gitStatus) {
    Write-Host "  WARNING: Uncommitted changes in backend-server/:" -ForegroundColor Yellow
    Write-Host $gitStatus -ForegroundColor DarkYellow
} else {
    Write-Host "  All files committed" -ForegroundColor Green
}

# Step 6: Rebuild from scratch
Write-Host "[6/6] Starting fresh build..." -ForegroundColor Yellow
Write-Host "  This will take 5-10 minutes for backend (Gradle download + compile)" -ForegroundColor DarkYellow
Write-Host "  Press Ctrl+C to cancel`n" -ForegroundColor DarkYellow

$args = @{
    ServerIp = $ServerIp
}
if ($BuildAndroid) { $args.BuildAndroid = $true }

& "$ProjectRoot\scripts\local-first-run.ps1" @args