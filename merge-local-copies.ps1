# merge-local-copies.ps1 — دمج كل نسخ المشروع المحلية في نسخة واحدة ورفعها إلى GitHub
# -----------------------------------------------------------------------------
# يفحص كل مجلدات المشروع على جهازك (Windows)، يجمع كل الملفات الفريدة/الأحدث
# في المجلد الرئيسي Pictures\pro، يلتزم كل شيء، ويرفع إلى فرع جديد على GitHub
# باسم local-full-merge — ثم يستلمه الذكاء الاصطناعي ويدمجه في المشروع المتكامل.
#
# التشغيل:  powershell -ExecutionPolicy Bypass -File merge-local-copies.ps1
$ErrorActionPreference = "Continue"

$dest = "C:\Users\hpc01\Pictures\pro"
$sources = @(
  "C:\Users\hpc01\AndroidStudioProjects\pro",
  "C:\Users\hpc01\Pictures\pro-arena-security"
)
$skipDirs = @('.git','node_modules','build','dist','.gradle','.idea','.vs','.cache','__pycache__','out','target','.venv','.terraform')

Write-Host "════════════════════════════════════════════════════════" -ForegroundColor Green
Write-Host "   دمج كل نسخ المشروع المحلية -> مشروع واحد متكامل"
Write-Host "════════════════════════════════════════════════════════" -ForegroundColor Green

if (-not (Test-Path $dest)) {
  Write-Host "❌ المجلد الرئيسي غير موجود: $dest" -ForegroundColor Red
  exit 1
}
Set-Location $dest

# ── 1) تأمين الحالة الحالية ─────────────────────────────────────────────────
Write-Host "`n[1/4] حفظ الحالة الحالية (كل الملفات + التقارير)..." -ForegroundColor Yellow
git config user.name "Ayman" 2>$null
git config user.email "Ayman123123123@users.noreply.github.com" 2>$null
git add -A
git commit -m "نسخة كاملة من جهازي: كل الملفات والتقارير المحلية" --allow-empty | Out-Null
Write-Host "   ✅ تم الالتزام" -ForegroundColor Green

# ── 2) دمج كل المصادر الأخرى (كل الملفات، بلا حذف أي شيء) ───────────────────
Write-Host "`n[2/4] دمج النسخ المحلية الأخرى..." -ForegroundColor Yellow
$ops = 0
foreach ($s in $sources) {
  if (-not (Test-Path $s)) { Write-Host "   (غير موجود: $s)" -ForegroundColor DarkGray; continue }
  Write-Host "   ← $s" -ForegroundColor Cyan
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
      Write-Host "     ↑ $rel" -ForegroundColor Yellow
    }
  }
}
Write-Host "   (تم دمج $ops ملف/تحديث)" -ForegroundColor Green

# ── 3) الالتزام بالملفات المدمجة ─────────────────────────────────────────────
Write-Host "`n[3/4] الالتزام بالملفات المدمجة..." -ForegroundColor Yellow
git add -A
git commit -m "دمج كل النسخ المحلية في مشروع واحد متكامل" --allow-empty | Out-Null
Write-Host "   ✅ تم الالتزام" -ForegroundColor Green

# ── 4) الرفع إلى GitHub ──────────────────────────────────────────────────────
Write-Host "`n[4/4] الرفع إلى GitHub (فرع جديد: local-full-merge)..." -ForegroundColor Yellow
git push origin HEAD:local-full-merge 2>&1 | ForEach-Object { Write-Host "   $_" }
if ($LASTEXITCODE -eq 0) {
  Write-Host "`n════════════════════════════════════════════════════════" -ForegroundColor Green
  Write-Host "  ✅ تم الرفع بنجاح إلى الفرع: local-full-merge" -ForegroundColor Green
  Write-Host "  سيتسلمه الذكاء الاصطناعي الآن ويدمجه في المشروع المتكامل." -ForegroundColor Green
  Write-Host "════════════════════════════════════════════════════════" -ForegroundColor Green
} else {
  Write-Host "`n⚠️ فشل الرفع. جرّب تسجيل الدخول ثم أعد تشغيل السكربت:" -ForegroundColor Red
  Write-Host "   gh auth login" -ForegroundColor Yellow
  Write-Host "   powershell -ExecutionPolicy Bypass -File merge-local-copies.ps1" -ForegroundColor Yellow
}
