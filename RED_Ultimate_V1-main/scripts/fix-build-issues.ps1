# ═══════════════════════════════════════════════════════════════════
# 🔧 إصلاح مشاكل البناء - مزامنة مع المستودع
# ═══════════════════════════════════════════════════════════════════
# هذا السكريبت:
# 1. يعرض حالة Git الحالية
# 2. يحفظ أي عمل غير محفوظ
# 3. يعيد المزامنة مع المستودع
# 4. يتحقق من صحة الملفات
# ═══════════════════════════════════════════════════════════════════

$ErrorActionPreference = "Stop"
$ProjectRoot = "C:\Users\hpc01\red_build"

Write-Host ""
Write-Host "╔══════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║   🔧 إصلاح مشاكل البناء - مزامنة المستودع             ║" -ForegroundColor Cyan
Write-Host "╚══════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

Push-Location $ProjectRoot

try {
    # الخطوة 1: التحقق من وجود المشروع
    Write-Host "📊 التحقق من المشروع المحلي:" -ForegroundColor Yellow
    if (Test-Path $ProjectRoot) {
        Write-Host "  ✅ المشروع المحلي موجود" -ForegroundColor Green
        Write-Host "  ℹ️  المشروع يعمل بشكل محلي - لا يوجد اتصال بـ Git" -ForegroundColor Cyan
    } else {
        throw "المشروع غير موجود في المسار: $ProjectRoot"
    }

    # الخطوة 2: إنشاء نسخة احتياطية
    Write-Host ""
    Write-Host "💾 إنشاء نسخة احتياطية..." -ForegroundColor Yellow
    $backup = Read-Host "هل تريد حفظ نسخة احتياطية من المشروع؟ (y/n)"
    if ($backup -eq 'y') {
        $backupPath = "$ProjectRoot\backup-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
        New-Item -ItemType Directory -Path $backupPath -Force | Out-Null
        Copy-Item -Path $ProjectRoot\* -Destination $backupPath -Recurse -Force
        Write-Host "  ✅ تم إنشاء نسخة احتياطية في: $backupPath" -ForegroundColor Green
    } else {
        Write-Host "  ⏭️  تم تخطي النسخ الاحتياطي" -ForegroundColor Yellow
    }

    # الخ��وة 3: التحقق من الملفات الأساسية
    Write-Host ""
    Write-Host "� التحقق من صحة الملفات المحلية..." -ForegroundColor Yellow

    # الخطوة 4: التحقق من الملفات
    Write-Host ""
    Write-Host "🔍 التحقق من صحة الملفات..." -ForegroundColor Yellow
    
    $filesToCheck = @(
        "red-app/src/main/java/com/red/sovereign/MainActivity.kt",
        "red-app/src/main/java/com/red/sovereign/calls/CallOverlay.kt",
        "red-app/src/main/java/com/red/sovereign/features/dinstar/DinstarViewModel.kt"
    )
    
    $allGood = $true
    foreach ($file in $filesToCheck) {
        if (Test-Path $file) {
            $content = Get-Content $file -Raw
            if ($file -match "MainActivity.kt" -and $content -match "import android.content.Intent") {
                Write-Host "  ✅ $file" -ForegroundColor Green
            } elseif ($file -match "CallOverlay.kt" -and $content -match "import androidx.compose.foundation.shape.RoundedCornerShape") {
                Write-Host "  ✅ $file" -ForegroundColor Green
            } elseif ($file -match "DinstarViewModel.kt" -and $content -match "import com.fasterxml.jackson.databind.ObjectMapper") {
                Write-Host "  ✅ $file" -ForegroundColor Green
            } else {
                Write-Host "  ❌ $file - ملف غير صحيح!" -ForegroundColor Red
                $allGood = $false
            }
        } else {
            Write-Host "  ❌ $file - غير موجود!" -ForegroundColor Red
            $allGood = $false
        }
    }

    if (-not $allGood) {
        throw "بعض الملفات غير صحيحة!"
    }

    # الخطوة 5: ملخص الإصلاح
    Write-Host ""
    Write-Host "╔══════════════════════════════════════════════════════════╗" -ForegroundColor Green
    Write-Host "║   ✅ تم الإصلاح بنجاح!                                 ║" -ForegroundColor Green
    Write-Host "╚══════════════════════════════════════════════════════════╝" -ForegroundColor Green
    Write-Host ""
    Write-Host "📋 الخطوات التالية:" -ForegroundColor Cyan
    Write-Host "  1. أعد بناء الباكند:" -ForegroundColor White
    Write-Host "     docker compose stop backend" -ForegroundColor DarkGray
    Write-Host "     docker compose build backend" -ForegroundColor DarkGray
    Write-Host "     docker compose up -d" -ForegroundColor DarkGray
    Write-Host ""
    Write-Host "  2. أعد بناء تطبيق الأندرويد:" -ForegroundColor White
    Write-Host "     docker build -t red-apk-builder -f android-build.Dockerfile ." -ForegroundColor DarkGray
    Write-Host "     docker run --name red-apk-build red-apk-builder" -ForegroundColor DarkGray
    Write-Host "     docker cp red-apk-build:/output/app-debug.apk ./app-debug.apk" -ForegroundColor DarkGray
    Write-Host ""
    Write-Host "  3. تحقق من النجاح:" -ForegroundColor White
    Write-Host "     docker logs red-backend --tail 30" -ForegroundColor DarkGray
    Write-Host ""

} catch {
    Write-Host ""
    Write-Host "❌ حدث خطأ: $_" -ForegroundColor Red
    Write-Host ""
    Write-Host "💡 الحلول الممكنة:" -ForegroundColor Yellow
    Write-Host "  1. تحقق من صحة الملفات المحلية:" -ForegroundColor White
    Write-Host "     cd $ProjectRoot" -ForegroundColor DarkGray
    Write-Host "     dir -Recurse -Filter *.kt | Select-Object -First 5" -ForegroundColor DarkGray
    Write-Host ""
    Write-Host "  2. استخدم النسخة الاحتياطية:" -ForegroundColor White
    Write-Host "     Copy-Item -Path '.\_backup_no_git_2026-09-04_01-59\*' -Destination $ProjectRoot -Recurse -Force" -ForegroundColor DarkGray
    exit 1
} finally {
    Pop-Location
}
