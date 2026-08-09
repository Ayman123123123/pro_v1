# فاحص: أي مجلد هو مشروع pro_v1 الحقيقي على جهازك؟
# شغّل هذا السكربت في PowerShell على جهازك (Windows) — سيكشف أي المسارين
# يحتوي المشروع الحقيقي المرفوع على GitHub (Ayman123123123/pro_v1).

$paths = @(
  "C:\Users\hpc01\Pictures\pro",
  "C:\Users\hpc01\AndroidStudioProjects\pro"
)

foreach ($p in $paths) {
  Write-Host ""
  Write-Host "==================== $p ====================" -ForegroundColor Cyan
  if (-not (Test-Path $p)) {
    Write-Host "❌ المجلد غير موجود" -ForegroundColor Red
    continue
  }
  # 1) هل هو مستودع git؟
  $gitDir = Join-Path $p ".git"
  if (Test-Path $gitDir) {
    Write-Host "✅ مستودع git"
    git -C $p remote -v
  } else {
    Write-Host "⚠️  ليس مستودع git (قد يكون مجلد استخراج ZIP عادي)"
  }

  # 2) العلامات المميزة للمشروع
  $markers = @(
    "RED_Ultimate_V1-main",
    "RED_Ultimate",
    "RED_Ultimate\build.gradle.kts",
    "RED_Ultimate\settings.gradle.kts",
    "RED_Ultimate\docker-compose.yml",
    "RED_Ultimate\gradlew.bat",
    "TECHNICAL_REPORT_AR.md"
  )
  foreach ($m in $markers) {
    $full = Join-Path $p $m
    if (Test-Path $full) { Write-Host "   ✅ $m" }
  }

  # 3) الحجم
  $size = (Get-ChildItem $p -Recurse -File -ErrorAction SilentlyContinue | Measure-Object -Property Length -Sum).Sum / 1MB
  Write-Host ("   📦 الحجم: {0:N0} MB" -f $size)
}
Write-Host ""
Write-Host "الخلاصة: المجلد الذي يظهر فيه '✅ RED_Ultimate' و'✅ git remote → pro_v1' هو المشروع الحقيقي."
