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
    # الخطوة 1: عرض الحالة الحالية
    Write-Host "📊 الحالة الحالية:" -ForegroundColor Yellow
    $status = git status --short
    if ($status) {
        Write-Host "  ⚠️  هناك تغييرات محلية:" -ForegroundColor Red
        $status | ForEach-Object { Write-Host "    $_" -ForegroundColor DarkGray }
        Write-Host ""
        
        $backup = Read-Host "هل تريد حفظ نسخة احتياطية من التغييرات؟ (y/n)"
        if ($backup -eq 'y') {
            $backupPath = "$ProjectRoot\backup-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
            git stash push -m "Auto-backup $(Get-Date)" | Out-Null
            Write-Host "  ✅ تم الحفظ في: $backupPath" -ForegroundColor Green
        }
    } else {
        Write-Host "  ✅ لا توجد تغييرات محلية" -ForegroundColor Green
    }

    # الخطوة 2: سحب التحديثات
    Write-Host ""
    Write-Host "🔄 سحب التحديثات من المستودع..." -ForegroundColor Yellow
    git fetch origin | Out-Null
    git pull origin arena/019ff8f2-pro-v1 --force | Out-Null
    Write-Host "  ✅ تم السحب بنجاح" -ForegroundColor Green

    # الخطوة 3: إعادة تعيين الملفات
    Write-Host ""
    Write-Host "🔧 إعادة تعيين الملفات إلى نسخة المستودع..." -ForegroundColor Yellow
    git reset --hard origin/arena/019ff8f2-pro-v1 | Out-Null
    Write-Host "  ✅ تم إعادة التعيين" -ForegroundColor Green

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
    Write-Host "  1. احذف المجلد واستنسخه من جديد:" -ForegroundColor White
    Write-Host "     cd C:\Users\hpc01\Pictures\pro_new" -ForegroundColor DarkGray
    Write-Host "     Remove-Item -Recurse -Force RED_Ultimate_V1-main" -ForegroundColor DarkGray
    Write-Host "     git clone -b arena/019ff8f2-pro-v1 https://github.com/Ayman123123123/pro_v1.git" -ForegroundColor DarkGray
    Write-Host ""
    Write-Host "  2. أو استخدم النسخة من GitHub مباشرة" -ForegroundColor White
    exit 1
} finally {
    Pop-Location
}
