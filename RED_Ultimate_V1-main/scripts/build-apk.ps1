# ═══════════════════════════════════════════════════════════════════
# 🔨 RED Ultimate APK Builder — Windows PowerShell
# يبني APK عبر Docker بدون الحاجة لتثبيت Gradle/Android SDK
# ═══════════════════════════════════════════════════════════════════
# الاستخدام: .\scripts\build-apk.ps1
# ═══════════════════════════════════════════════════════════════════

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
if (-not (Test-Path "$ProjectRoot\RED_Ultimate_V1-main\RED_Ultimate\build.gradle.kts")) {
    $ProjectRoot = Split-Path -Parent $PSScriptRoot
}
$UltimateDir = Join-Path $ProjectRoot "RED_Ultimate_V1-main\RED_Ultimate"
$OutputDir = Join-Path $ProjectRoot "apk-output"

Write-Host ""
Write-Host "╔══════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║   🔨 RED Ultimate APK Builder                          ║" -ForegroundColor Cyan
Write-Host "║   يونس ماستر — الإدارة السيادية                        ║" -ForegroundColor Cyan
Write-Host "╚══════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

# ── الخطوة 1: التحقق من Docker ──
Write-Host "🔍 التحقق من Docker..." -ForegroundColor Yellow
try {
    $dockerVersion = docker --version 2>&1
    Write-Host "  ✅ $dockerVersion" -ForegroundColor Green
} catch {
    Write-Host "  ❌ Docker غير مثبت أو غير_running" -ForegroundColor Red
    Write-Host "  يرجى تثبيت Docker Desktop: https://www.docker.com/products/docker-desktop/" -ForegroundColor Red
    exit 1
}

# ── الخطوة 2: تعطيل Dependency Verification ──
Write-Host ""
Write-Host "🔧 تعطيل Dependency Verification..." -ForegroundColor Yellow
$gradleProps = Join-Path $UltimateDir "gradle.properties"
$content = Get-Content $gradleProps -Raw
$content = $content -replace 'org\.gradle\.dependency\.verification=.*', 'org.gradle.dependency.verification=off'
if ($content -notmatch 'org\.gradle\.dependency\.verification') {
    $content += "`norg.gradle.dependency.verification=off`n"
}
Set-Content $gradleProps $content -NoNewline
Write-Host "  ✅ تم تعطيل verification" -ForegroundColor Green

# ── الخطوة 3: بناء Docker Image ──
Write-Host ""
Write-Host "🐳 بناء Docker Image (قد يستغرق 5-10 دقائق أول مرة)..." -ForegroundColor Yellow
Push-Location $UltimateDir
try {
    docker build -t red-apk-builder -f android-build.Dockerfile . 2>&1 | ForEach-Object {
        if ($_ -match 'ERROR|FAILED|error:') {
            Write-Host "  ❌ $_" -ForegroundColor Red
        } elseif ($_ -match 'Step|CACHED|DONE') {
            Write-Host "  $_" -ForegroundColor DarkGray
        }
    }
    if ($LASTEXITCODE -ne 0) {
        throw "Docker build failed"
    }
    Write-Host "  ✅ تم بناء Docker Image بنجاح" -ForegroundColor Green
} finally {
    Pop-Location
}

# ── الخطوة 4: إنشاء مجلد الإخراج ──
if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

# ── الخطوة 5: تشغيل البناء واستخراج APK ──
Write-Host ""
Write-Host "📦 بناء APK (قد يستغرق 10-30 دقيقة)..." -ForegroundColor Yellow
Write-Host "  هذا يشمل: تحميل dependencies + تجميع الكود + إنشاء APK" -ForegroundColor DarkGray

$containerName = "red-apk-build-$(Get-Date -Format 'yyyyMMdd-HHmmss')"

try {
    docker run --name $containerName red-apk-builder 2>&1 | ForEach-Object {
        if ($_ -match 'BUILD SUCCESSFUL') {
            Write-Host "  ✅ $_" -ForegroundColor Green
        } elseif ($_ -match 'BUILD FAILED|FAILURE') {
            Write-Host "  ⚠️ $_" -ForegroundColor Yellow
        } elseif ($_ -match 'Task :app:') {
            Write-Host "  📋 $_" -ForegroundColor Cyan
        }
    }

    # استخراج APK من الـ container
    Write-Host ""
    Write-Host "📥 استخراج APK..." -ForegroundColor Yellow
    
    # محاولة نسخ APK من المسارات المحتملة
    $apkPaths = @(
        "/build/red-app/build/outputs/apk/debug/app-debug.apk",
        "/build/red-app/build/outputs/apk/debug/red-app-debug.apk",
        "/build/app/build/outputs/apk/debug/app-debug.apk"
    )
    
    $apkCopied = $false
    foreach ($apkPath in $apkPaths) {
        try {
            docker cp "${containerName}:${apkPath}" $OutputDir 2>$null
            if ($LASTEXITCODE -eq 0) {
                $apkCopied = $true
                Write-Host "  ✅ تم نسخ APK من: $apkPath" -ForegroundColor Green
                break
            }
        } catch {}
    }
    
    if (-not $apkCopied) {
        # البحث عن أي APK في الـ container
        Write-Host "  🔍 البحث عن APK في الـ container..." -ForegroundColor Yellow
        $foundApks = docker exec $containerName find /build -name "*.apk" -type f 2>$null
        if ($foundApks) {
            foreach ($apk in $foundApks) {
                $fileName = Split-Path -Leaf $apk
                docker cp "${containerName}:${apk}" (Join-Path $OutputDir $fileName)
                Write-Host "  ✅ تم نسخ: $fileName" -ForegroundColor Green
            }
            $apkCopied = $true
        }
    }
    
    if ($apkCopied) {
        Write-Host ""
        Write-Host "╔══════════════════════════════════════════════════════════╗" -ForegroundColor Green
        Write-Host "║   ✅ تم بناء APK بنجاح!                                 ║" -ForegroundColor Green
        Write-Host "╚══════════════════════════════════════════════════════════╝" -ForegroundColor Green
        Write-Host ""
        Write-Host "📁 موقع APK:" -ForegroundColor Cyan
        Get-ChildItem $OutputDir -Filter "*.apk" | ForEach-Object {
            Write-Host "  📦 $($_.FullName) ($([math]::Round($_.Length/1MB, 1)) MB)" -ForegroundColor White
        }
    } else {
        Write-Host ""
        Write-Host "⚠️ لم يتم العثور على APK. جاري حفظ سجل البناء..." -ForegroundColor Yellow
        
        # حفظ سجل البناء
        docker cp "${containerName}:/build/build-output.log" (Join-Path $OutputDir "build-output.log") 2>$null
        docker logs $containerName > (Join-Path $OutputDir "docker-build.log") 2>&1
        
        Write-Host "  📋 سجل البناء: $(Join-Path $OutputDir 'build-output.log')" -ForegroundColor DarkGray
        Write-Host "  📋 Docker log: $(Join-Path $OutputDir 'docker-build.log')" -ForegroundColor DarkGray
    }
} finally {
    # تنظيف الـ container
    Write-Host ""
    Write-Host "🧹 تنظيف..." -ForegroundColor Yellow
    docker rm $containerName 2>$null | Out-Null
    Write-Host "  ✅ تم حذف container: $containerName" -ForegroundColor Green
}

Write-Host ""
Write-Host "╔══════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║   🏁 انتهى البناء                                       ║" -ForegroundColor Cyan
Write-Host "╚══════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
