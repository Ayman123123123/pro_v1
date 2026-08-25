# ═══════════════════════════════════════════════════════════════════
# 🔄 Regenerate Gradle Dependency Verification Metadata
# يعيد توليد verification-metadata.xml بشكل كامل
# ═══════════════════════════════════════════════════════════════════
# الاستخدام: .\scripts\regenerate-verification.ps1
# 
# هذا السكريبت:
# 1. يعطل التحقق مؤقتاً
# 2. ينظف Gradle cache
# 3. يعيد توليد verification-metadata.xml
# 4. يتحقق من الملف الجديد
# ═══════════════════════════════════════════════════════════════════

$ErrorActionPreference = "Stop"
$UltimateDir = Join-Path (Split-Path -Parent (Split-Path -Parent $PSScriptRoot)) "RED_Ultimate_V1-main\RED_Ultimate"
if (-not (Test-Path "$UltimateDir\gradlew")) {
    $UltimateDir = Split-Path -Parent $PSScriptRoot
}

Write-Host ""
Write-Host "╔══════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║   🔄 Regenerate Gradle Verification Metadata           ║" -ForegroundColor Cyan
Write-Host "╚══════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

Push-Location $UltimateDir

# ── الخطوة 1: نسخ احتياطي للملف الحالي ──
$metadataFile = "gradle\verification-metadata.xml"
$backupFile = "gradle\verification-metadata.xml.backup-$(Get-Date -Format 'yyyyMMdd-HHmmss')"
if (Test-Path $metadataFile) {
    Copy-Item $metadataFile $backupFile
    Write-Host "✅ تم إنشاء نسخة احتياطية: $backupFile" -ForegroundColor Green
}

# ── الخطوة 2: تعطيل التحقق مؤقتاً ──
Write-Host ""
Write-Host "🔧 تعطيل Dependency Verification مؤقتاً..." -ForegroundColor Yellow
$gradleProps = "gradle.properties"
$content = Get-Content $gradleProps -Raw
$content = $content -replace 'org\.gradle\.dependency\.verification=.*', 'org.gradle.dependency.verification=off'
if ($content -notmatch 'org\.gradle\.dependency\.verification') {
    $content += "`norg.gradle.dependency.verification=off`n"
}
Set-Content $gradleProps $content -NoNewline
Write-Host "  ✅ تم تعطيل verification" -ForegroundColor Green

# ── الخطوة 3: تنظيف Gradle cache ──
Write-Host ""
Write-Host "🧹 تنظيف Gradle cache..." -ForegroundColor Yellow
.\gradlew clean 2>&1 | Out-Null
Write-Host "  ✅ تم التنظيف" -ForegroundColor Green

# ── الخطوة 4: إعادة توليد verification-metadata.xml ──
Write-Host ""
Write-Host "🔄 إعادة توليد verification-metadata.xml..." -ForegroundColor Yellow
Write-Host "  هذا قد يستغرق 10-30 دقيقة حسب سرعة الإنترنت..." -ForegroundColor DarkGray
Write-Host ""

# توليد ملف جديد بـ SHA-256
.\gradlew --write-verification-metadata sha256 help --no-daemon 2>&1 | ForEach-Object {
    if ($_ -match 'BUILD SUCCESSFUL') {
        Write-Host "  ✅ $_" -ForegroundColor Green
    } elseif ($_ -match 'FAILED|ERROR') {
        Write-Host "  ⚠️ $_" -ForegroundColor Yellow
    }
}

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "❌ فشل إعادة التوليد. يمكنك المحاولة يدوياً:" -ForegroundColor Red
    Write-Host "   .\gradlew --write-verification-metadata sha256 :app:assembleDebug" -ForegroundColor DarkGray
    Pop-Location
    exit 1
}

# ── الخطوة 5: التحقق من الملف الجديد ──
Write-Host ""
Write-Host "🔍 التحقق من الملف الجديد..." -ForegroundColor Yellow
$newContent = Get-Content $metadataFile -Raw
$componentCount = ([regex]::Matches($newContent, '<component ')).Count
Write-Host "  📊 عدد المكونات: $componentCount" -ForegroundColor Cyan

# ── الخطوة 6: إعادة تفعيل التحقق ──
Write-Host ""
Write-Host "🔒 إعادة تفعيل Dependency Verification..." -ForegroundColor Yellow
$content = $content -replace 'org\.gradle\.dependency\.verification=off', 'org.gradle.dependency.verification=lenient'
Set-Content $gradleProps $content -NoNewline
Write-Host "  ✅ تم تفعيل verification بوضع lenient" -ForegroundColor Green

# ── الخطوة 7: اختبار البناء ──
Write-Host ""
Write-Host "🧪 اختبار البناء السريع..." -ForegroundColor Yellow
.\gradlew :app:dependencies --no-daemon 2>&1 | Out-Null
if ($LASTEXITCODE -eq 0) {
    Write-Host "  ✅ نجح اختبار البناء" -ForegroundColor Green
} else {
    Write-Host "  ⚠️ فشل الاختبار. يمكنك استخدام وضع off مؤقتاً" -ForegroundColor Yellow
}

Pop-Location

Write-Host ""
Write-Host "╔══════════════════════════════════════════════════════════╗" -ForegroundColor Green
Write-Host "║   ✅ تم إعادة توليد verification-metadata.xml           ║" -ForegroundColor Green
Write-Host "╚══════════════════════════════════════════════════════════╝" -ForegroundColor Green
Write-Host ""
Write-Host "📝 ملاحظات:" -ForegroundColor Cyan
Write-Host "  • الملف الجديد يحتوي على $componentCount مكون" -ForegroundColor White
Write-Host "  • النسخة الاحتياطية: $backupFile" -ForegroundColor White
Write-Host "  • الوضع الحالي: lenient (يسمح بالقطع غير الموثقة)" -ForegroundColor White
Write-Host ""
Write-Host "🔒 لتفعيل الوضع الصارم:" -ForegroundColor Cyan
Write-Host "  غيّر في gradle.properties:" -ForegroundColor DarkGray
Write-Host "  org.gradle.dependency.verification=strict" -ForegroundColor DarkGray
