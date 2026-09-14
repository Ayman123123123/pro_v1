# ══════════════════════════════════════════════════════════════════════
# 🏥 RED Sovereign PSTN Health Check — Legendary Diagnostic Suite
# يفحص كل طبقات النظام: Docker → Asterisk → Dinstar → Backend → Network
# ══════════════════════════════════════════════════════════════════════
# الاستخدام:
#   .\scripts\pstn-health-check.ps1
#   .\scripts\pstn-health-check.ps1 -Verbose    # تفاصيل إضافية
#   .\scripts\pstn-health-check.ps1 -Fix        # محاولة إصلاح تلقائي
# ══════════════════════════════════════════════════════════════════════

param(
    [switch]$Fix,
    [switch]$Verbose
)

$ErrorActionPreference = "Continue"
$checks = @()
$fixes = @()

function Add-Check {
    param([string]$Name, [bool]$Pass, [string]$Detail = "", [string]$FixCmd = "")
    $script:checks += [PSCustomObject]@{
        Name = $Name
        Status = if ($Pass) { "✅ PASS" } else { "❌ FAIL" }
        Detail = $Detail
        Fix = $FixCmd
    }
    if (-not $Pass -and $FixCmd -and $Fix) {
        Write-Host "  🔧 Auto-fixing: $Name..." -ForegroundColor Yellow
        try {
            Invoke-Expression $FixCmd
            $script:fixes += $Name
        } catch {
            Write-Host "  ⚠️ Fix failed: $_" -ForegroundColor Red
        }
    }
}

Write-Host ""
Write-Host "╔══════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║   🏥 RED Sovereign PSTN Health Check                    ║" -ForegroundColor Cyan
Write-Host "║   $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')                              ║" -ForegroundColor Gray
Write-Host "╚══════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

# ── 1. Docker Daemon ──────────────────────────────────────────────
Write-Host "━━━ 1. Docker Daemon ━━━" -ForegroundColor Yellow
try {
    $dv = docker version --format '{{.Server.Version}}' 2>$null
    Add-Check "Docker daemon running" ($null -ne $dv) "Server: $dv"
} catch {
    Add-Check "Docker daemon running" $false "Daemon not responding"
    Write-Host "  ⏳ Docker Desktop is still starting. Wait for 'Docker Desktop is running'." -ForegroundColor Red
    Write-Host ""
    $checks | Format-Table -AutoSize
    exit 1
}

# ── 2. Containers ─────────────────────────────────────────────────
Write-Host "━━━ 2. Containers ━━━" -ForegroundColor Yellow
$expected = @("red-pstn-gateway", "red-backend", "red-proxy", "red-turn", "red-media-sfu",
              "red-cache", "red-db-sql", "red-db-nosql", "red-storage", "red-admin-ui")
foreach ($c in $expected) {
    $status = docker inspect $c --format '{{.State.Status}} ({{.State.Health.Status}})' 2>$null
    $running = $status -match "running"
    Add-Check "Container $c" $running $status "docker compose up -d $c"
}

# ── 3. Asterisk PJSIP ─────────────────────────────────────────────
Write-Host "━━━ 3. Asterisk PJSIP ━━━" -ForegroundColor Yellow
try {
    $eps = docker exec red-pstn-gateway asterisk -rx 'pjsip show endpoints' 2>$null
    $port7 = $eps | Select-String "dinstar-gw-192-168-11-1-port-7" | Select-String "Avail"
    Add-Check "Port 7 endpoint Avail" ($null -ne $port7) ($port7 -join " ")

    $webrtc = docker exec red-pstn-gateway asterisk -rx 'pjsip show endpoint red-webrtc-client' 2>$null
    $media = ($webrtc | Select-String "media_address").ToString().Trim()
    $mediaOk = $media -match "10\.38\.160\.42"
    Add-Check "WebRTC media_address=10.38.160.42" $mediaOk $media

    $ice = ($webrtc | Select-String "ice_support\s+:\s+true").Count -gt 0
    Add-Check "WebRTC ICE support" $ice ""

    $timers = ($webrtc | Select-String "timers\s+:\s+yes").Count -gt 0
    Add-Check "SIP session timers" $timers ""
} catch {
    Add-Check "Asterisk PJSIP query" $false $_.Exception.Message
}

# ── 4. RTP Settings ───────────────────────────────────────────────
Write-Host "━━━ 4. RTP Settings ━━━" -ForegroundColor Yellow
try {
    $rtp = docker exec red-pstn-gateway asterisk -rx 'rtp show settings' 2>$null
    $start = ($rtp | Select-String "Port start").ToString() -match "10000"
    $end = ($rtp | Select-String "Port end").ToString() -match "20000"
    Add-Check "RTP range 10000-20000" ($start -and $end) (($rtp | Select-String "Port (start|end)") -join " | ")
} catch {
    Add-Check "RTP settings query" $false $_.Exception.Message
}

# ── 5. Dinstar Reachability ───────────────────────────────────────
Write-Host "━━━ 5. Dinstar Gateway ━━━" -ForegroundColor Yellow
$ping = Test-Connection -ComputerName 192.168.11.1 -Count 2 -Quiet -ErrorAction SilentlyContinue
Add-Check "Dinstar 192.168.11.1 ping" $ping "Gateway management interface"

try {
    $tcp = Test-NetConnection -ComputerName 192.168.11.1 -Port 443 -WarningAction SilentlyContinue
    Add-Check "Dinstar HTTPS 443" $tcp.TcpTestSucceeded ""
} catch {
    Add-Check "Dinstar HTTPS 443" $false $_.Exception.Message
}

# ── 6. Backend Health ─────────────────────────────────────────────
Write-Host "━━━ 6. Backend ━━━" -ForegroundColor Yellow
try {
    $h = Invoke-RestMethod -Uri "http://localhost:8088/health" -TimeoutSec 10 -ErrorAction Stop
    Add-Check "Backend /health" ($h.status -eq "UP") ($h | ConvertTo-Json -Compress)
} catch {
    Add-Check "Backend /health" $false $_.Exception.Message
}

# ── 7. TURN Server ────────────────────────────────────────────────
Write-Host "━━━ 7. TURN Server ━━━" -ForegroundColor Yellow
try {
    $turn = Test-NetConnection -ComputerName 10.38.160.42 -Port 3478 -WarningAction SilentlyContinue
    Add-Check "TURN 10.38.160.42:3478" $turn.TcpTestSucceeded ""
} catch {
    Add-Check "TURN 10.38.160.42:3478" $false $_.Exception.Message
}

# ── 8. Config Files ───────────────────────────────────────────────
Write-Host "━━━ 8. Config Files ━━━" -ForegroundColor Yellow
$envContent = Get-Content ".env" -Raw -ErrorAction SilentlyContinue
Add-Check ".env TURN_PUBLIC_HOST=10.38.160.42" ($envContent -match "TURN_PUBLIC_HOST=10\.38\.160\.42") ""
Add-Check ".env PORT_7_PRIMARY=785681741" ($envContent -match "PORT_7_PRIMARY_PHONE_NUMBER=785681741") ""

# ── Summary ───────────────────────────────────────────────────────
Write-Host ""
Write-Host "╔══════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║   📊 SUMMARY                                           ║" -ForegroundColor Cyan
Write-Host "╚══════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
$checks | Format-Table -AutoSize

$passed = ($checks | Where-Object { $_.Status -match "PASS" }).Count
$total = $checks.Count
Write-Host ""
Write-Host "  Result: $passed / $total checks passed" -ForegroundColor $(if ($passed -eq $total) { "Green" } else { "Yellow" })

if ($Fix -and $fixes.Count -gt 0) {
    Write-Host "  Auto-fixed: $($fixes -join ', ')" -ForegroundColor Green
}

$failed = $checks | Where-Object { $_.Status -match "FAIL" -and $_.Fix }
if ($failed.Count -gt 0) {
    Write-Host ""
    Write-Host "  💡 Suggested fixes:" -ForegroundColor Yellow
    $failed | ForEach-Object { Write-Host "     $($_.Name): $($_.Fix)" -ForegroundColor Gray }
}
Write-Host ""
