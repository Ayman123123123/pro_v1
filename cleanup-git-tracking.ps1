# cleanup-git-tracking.ps1 — untrack files that .gitignore now declares ignored.
#   RED Sovereign / YOUNES  ·  repo: D:\pro_new\pro_new
#
# لماذا هذا السكربت:
#   قواعد .gitignore لا تُلغي تتبّع ملفات متتبَّعة أصلاً. لذلك بقيت آلاف اللقطات
#   المرجعية وسجلات الانهيار وكاش SDK داخل المستودع رغم إصلاح القواعد. إلغاء
#   التتبّع يحتاج `git rm --cached` — وهذا ما يفعله هذا السكربت.
#
# الأمان (مهم):
#   * `git rm --cached` لا يحذف أي ملف من القرص — يزيله من الفهرس فقط.
#   * القائمة تُشتقّ من git نفسه (`--cached --ignored --exclude-standard`)، لا
#     من نمط مكتوب يدويًا هنا، فتتبع أي تعديل مستقبلي على .gitignore تلقائيًا.
#   * الوضع الافتراضي **dry-run**: يعرض ما سيفعل ثم يتوقف. لا يكتب شيئًا.
#   * لا يعمل commit ولا push. يطبع الأمر التالي فقط لتُنفّذه بنفسك.
#
# الاستخدام:
#   1) مراجعة (لا تغيير):  powershell -ExecutionPolicy Bypass -File .\cleanup-git-tracking.ps1
#   2) تنفيذ:              powershell -ExecutionPolicy Bypass -File .\cleanup-git-tracking.ps1 -Apply

param(
  [switch]$Apply,
  [string]$Repo = "D:\pro_new\pro_new"
)

$ErrorActionPreference = "Stop"
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8 } catch { }

Set-Location $Repo
git rev-parse --is-inside-work-tree | Out-Null

$branch = (git rev-parse --abbrev-ref HEAD).Trim()
$head   = (git log --oneline -1)

Write-Host "== 1/4 repository ==" -ForegroundColor Cyan
Write-Host "  repo   : $Repo"
Write-Host "  branch : $branch"
Write-Host "  head   : $head"

# المجموعة الأولى: متتبَّع + مُتجاهَل = يجب إلغاء تتبّعه.
Write-Host "== 2/4 tracked-but-ignored files ==" -ForegroundColor Cyan
$tracked = @(git ls-files --cached --ignored --exclude-standard)

if ($tracked.Count -eq 0) {
  Write-Host "  none - index already clean of ignored files." -ForegroundColor Green
} else {
  # تصنيف للعرض فقط: يساعد على تقدير حجم كل عائلة قبل التنفيذ.
  $groups = [ordered]@{
    "screenshot reference PNGs" = @($tracked | Where-Object { $_ -match '/src/screenshotTest[^/]*/reference/' })
    "JVM crash logs"            = @($tracked | Where-Object { $_ -match '(hs_err_pid|replay_pid).*\.log$' })
    "SDK / gradle caches"       = @($tracked | Where-Object { $_ -match '\.(android_home|gradle_home|gradle_user_home|gradle-user-home)/' })
    "git plumbing / bundles"    = @($tracked | Where-Object { $_ -match '(^|/)(\.git-sha1|\.git-temp|red-sha1)/|\.bundle$' })
    "build outputs"             = @($tracked | Where-Object { $_ -match '\.(apk|aab)$|/(build|node_modules|\.gradle)/' })
    "diagnostic text reports"   = @($tracked | Where-Object { $_ -match '(used_imports|imports_list|declared_deps|scratch_duplicates|errs|recovered|test_results|build_(log|out|output)|backend_(compile|test)|assemble_log|rebuild_log|logcat|build_errors.*)\.txt$' })
  }

  $classified = 0
  foreach ($k in $groups.Keys) {
    $n = $groups[$k].Count
    $classified += $n
    if ($n -gt 0) { Write-Host ("  {0,-28} {1,7}" -f $k, $n) }
  }
  $other = $tracked.Count - $classified
  if ($other -gt 0) { Write-Host ("  {0,-28} {1,7}" -f "(other ignored rules)", $other) }
  Write-Host ("  {0,-28} {1,7}" -f "TOTAL", $tracked.Count) -ForegroundColor Yellow

  # تحذير أمني: أي ملف أسرار متتبَّع = تسريب يجب إصلاحه قبل أي شيء آخر.
  $secrets = @($tracked | Where-Object { $_ -match '(^|/)\.env($|\.)|\.(pem|jks|keystore)$|cookies\.txt$|application-override\.properties$' })
  if ($secrets.Count -gt 0) {
    Write-Host "  !! SECRETS ARE TRACKED - report and rotate before pushing:" -ForegroundColor Red
    $secrets | ForEach-Object { Write-Host "     $_" -ForegroundColor Red }
  }

  Write-Host "  first 15 paths:" -ForegroundColor DarkGray
  $tracked | Select-Object -First 15 | ForEach-Object { Write-Host "    $_" -ForegroundColor DarkGray }
  if ($tracked.Count -gt 15) { Write-Host "    ... +$($tracked.Count - 15) more" -ForegroundColor DarkGray }
}

# المجموعة الثانية: ملفات متتبَّعة حُذفت من القرص (تبقى في الفهرس حتى تُثبَّت).
Write-Host "== 3/4 tracked files missing from disk ==" -ForegroundColor Cyan
$deleted = @(git ls-files --deleted)
if ($deleted.Count -eq 0) {
  Write-Host "  none." -ForegroundColor Green
} else {
  Write-Host "  $($deleted.Count) path(s) already gone from disk (their removal still needs committing):" -ForegroundColor Yellow
  $deleted | Select-Object -First 10 | ForEach-Object { Write-Host "    $_" -ForegroundColor DarkGray }
  if ($deleted.Count -gt 10) { Write-Host "    ... +$($deleted.Count - 10) more" -ForegroundColor DarkGray }
}

Write-Host "== 4/4 action ==" -ForegroundColor Cyan
if (-not $Apply) {
  Write-Host "  DRY RUN - nothing was changed." -ForegroundColor Green
  Write-Host "  Review the lists above, then run with -Apply to untrack them." -ForegroundColor Green
  exit 0
}

if ($tracked.Count -eq 0) {
  Write-Host "  Nothing to untrack." -ForegroundColor Green
  exit 0
}

# تنفيذ: إزالة من الفهرس فقط (بلا لمس القرص). على دفعات لتفادي حدّ سطر الأوامر.
Write-Host "  untracking $($tracked.Count) path(s) with 'git rm --cached' ..." -ForegroundColor Yellow
$batchSize = 200
$done = 0
for ($i = 0; $i -lt $tracked.Count; $i += $batchSize) {
  $end = [Math]::Min($i + $batchSize, $tracked.Count) - 1
  $slice = $tracked[$i..$end]
  $slice | git rm --cached --quiet --
  if ($LASTEXITCODE -ne 0) { throw "git rm --cached failed on batch starting at index $i" }
  $done += $slice.Count
  Write-Host "    $done / $($tracked.Count)" -ForegroundColor DarkGray
}

Write-Host "  done. Files are still on disk; only the index changed." -ForegroundColor Green
Write-Host ""
Write-Host "Next (review, then commit yourself - this script never commits or pushes):" -ForegroundColor Cyan
Write-Host "  git status --short | Measure-Object -Line" -ForegroundColor White
Write-Host "  git commit -m `"chore: untrack files now covered by .gitignore`"" -ForegroundColor White
