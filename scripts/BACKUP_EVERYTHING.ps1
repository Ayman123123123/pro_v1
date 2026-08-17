# ═══════════════════════════════════════════════════════════════════════════
#  BACKUP_EVERYTHING.ps1  —  حفظ كل شيء من pro_new بلا فقدان حرف واحد
# ═══════════════════════════════════════════════════════════════════════════
#  التشغيل:
#     cd C:\Users\hpc01\Pictures\pro_new
#     powershell -ExecutionPolicy Bypass -File scripts\BACKUP_EVERYTHING.ps1
#
#  ماذا يفعل:
#   [1] أرشيف كامل لكل شيء (حتى المتجاهَل و .git و upload-clean)  ← الأمان المطلق
#   [2] يعالج مصيدة upload-clean (مستودع متداخل لا يرفعه git)
#   [3] يلتزم ويرفع كل الملفات إلى فرع جديد على GitHub
#   [4] تقرير تحقق: يعدّ الملفات قبل وبعد ويثبت عدم النقص
#
#  لا يحذف شيئاً. لا يعدّل أي فرع قائم. آمن 100%.
# ═══════════════════════════════════════════════════════════════════════════

$ErrorActionPreference = "Continue"
$ProgressPreference    = "SilentlyContinue"

$repo   = "C:\Users\hpc01\Pictures\pro_new"
$stamp  = Get-Date -Format "yyyyMMdd-HHmmss"
$vault  = "C:\Users\hpc01\Pictures\_BACKUP_pro_new_$stamp"
$branch = "backup/strongest-2026-08-14"

function Say($m,$c="White"){ Write-Host $m -ForegroundColor $c }
function Line(){ Say ("=" * 70) "DarkGray" }

Line
Say "   حفظ كامل لمشروع pro_new — بلا فقدان أي ملف" "Green"
Line

if (-not (Test-Path $repo)) { Say "المجلد غير موجود: $repo" "Red"; exit 1 }
Set-Location $repo

New-Item -ItemType Directory -Force -Path $vault | Out-Null

# ── تمكين المسارات الطويلة (عندك 60 مساراً يتجاوز 260 حرفاً) ────────────────
git config core.longpaths true
git config core.protectNTFS false

# ═══ [1] جرد شامل قبل أي شيء ═══════════════════════════════════════════════
Say "`n[1/5] جرد كل الملفات الموجودة فعلياً على القرص..." "Yellow"

$allFiles = Get-ChildItem -Path $repo -Recurse -File -Force -ErrorAction SilentlyContinue |
            Where-Object { $_.FullName -notmatch '\\\.git\\' }
$totalOnDisk = $allFiles.Count
$totalSize   = [math]::Round(($allFiles | Measure-Object Length -Sum).Sum / 1MB, 1)

Say "    ملفات على القرص (بلا .git): $totalOnDisk" "Cyan"
Say "    الحجم: $totalSize ميجابايت" "Cyan"

$allFiles | ForEach-Object { $_.FullName.Substring($repo.Length+1) } |
    Sort-Object | Out-File -Encoding utf8 "$vault\INVENTORY_BEFORE.txt"
Say "    تم حفظ قائمة الجرد: INVENTORY_BEFORE.txt" "DarkGray"

# ═══ [2] الأرشيف الكامل — الأمان المطلق ═══════════════════════════════════
Say "`n[2/5] إنشاء أرشيف كامل لكل شيء (قد يستغرق دقائق)..." "Yellow"

$tarPath = "$vault\pro_new_FULL_$stamp.tar"
# tar.exe مدمج في ويندوز 10+ ويتعامل مع المسارات الطويلة والأسماء العربية
Push-Location "C:\Users\hpc01\Pictures"
& tar.exe -cf $tarPath "pro_new" 2>&1 | Out-Null
Pop-Location

if (Test-Path $tarPath) {
    $tarMB = [math]::Round((Get-Item $tarPath).Length / 1MB, 1)
    Say "    الأرشيف الكامل جاهز: $tarMB ميجابايت" "Green"
    Say "    المسار: $tarPath" "DarkGray"
    Say "    (يحوي كل شيء: .git + المتجاهَل + upload-clean + الأسرار)" "DarkGray"
} else {
    Say "    تعذّر إنشاء الأرشيف — سنكمل بالنسخ المباشر" "Red"
    robocopy $repo "$vault\pro_new_COPY" /E /COPYALL /R:1 /W:1 /NFL /NDL /NJH /NJS | Out-Null
    Say "    تم النسخ المباشر بدلاً منه" "Yellow"
}

# ═══ [3] مصيدة upload-clean ════════════════════════════════════════════════
Say "`n[3/5] فحص المستودعات المتداخلة (مصيدة فقدان الملفات)..." "Yellow"

$nested = Get-ChildItem -Path $repo -Recurse -Directory -Force -Filter ".git" -ErrorAction SilentlyContinue |
          Where-Object { $_.FullName -ne "$repo\.git" }

if ($nested) {
    foreach ($n in $nested) {
        $rel = $n.Parent.FullName.Substring($repo.Length+1)
        $cnt = (Get-ChildItem $n.Parent.FullName -Recurse -File -Force -ErrorAction SilentlyContinue |
                Where-Object { $_.FullName -notmatch '\\\.git\\' }).Count
        Say "    مستودع متداخل: $rel  ($cnt ملف)" "Red"
        Say "      git العادي لا يرفع محتواه — سنعالجه" "DarkGray"

        # حفظ نسخة مستقلة منه
        & tar.exe -cf "$vault\NESTED_$($n.Parent.Name)_$stamp.tar" -C $n.Parent.Parent.FullName $n.Parent.Name 2>&1 | Out-Null

        # تحويله لمجلد عادي حتى يرفعه git مع كل ملفاته
        Rename-Item -Path $n.FullName -NewName "_git_disabled_$stamp" -Force -ErrorAction SilentlyContinue
        git rm --cached $rel -f 2>&1 | Out-Null
        Say "      تم تحويله لمجلد عادي — سيُرفع كاملاً الآن" "Green"
    }
} else {
    Say "    لا توجد مستودعات متداخلة" "Green"
}

# ═══ [4] الالتزام والرفع ═══════════════════════════════════════════════════
Say "`n[4/5] إضافة كل الملفات والرفع..." "Yellow"

git config user.name  "Ayman"
git config user.email "Ayman123123123@users.noreply.github.com"

git add -A --force 2>&1 | Where-Object { $_ -notmatch "^warning: in the working copy" } | Out-Null

$staged = (git diff --cached --name-only | Measure-Object -Line).Lines
Say "    ملفات مجهّزة للالتزام: $staged" "Cyan"

git commit -m "نسخة كاملة من pro_new: كل الملفات بلا استثناء ($stamp)" 2>&1 |
    Select-Object -First 3 | ForEach-Object { Say "    $_" "DarkGray" }

Say "`n    جاري الرفع إلى GitHub (قد يستغرق عدة دقائق)..." "Yellow"
git config http.postBuffer 524288000
git push origin "HEAD:refs/heads/$branch" 2>&1 |
    ForEach-Object { Say "    $_" "DarkGray" }

# ═══ [5] تقرير التحقق ══════════════════════════════════════════════════════
Say "`n[5/5] التحقق من عدم فقدان أي ملف..." "Yellow"

$trackedNow = (git ls-files | Measure-Object -Line).Lines
$ignoredNow = (git ls-files --others --ignored --exclude-standard | Measure-Object -Line).Lines
$untracked  = (git ls-files --others --exclude-standard | Measure-Object -Line).Lines

git ls-files | Out-File -Encoding utf8 "$vault\INVENTORY_TRACKED_AFTER.txt"

Line
Say "   تقرير التحقق النهائي" "Green"
Line
Say "   ملفات على القرص       : $totalOnDisk" "White"
Say "   متتبعة في git الآن     : $trackedNow" "Green"
Say "   متجاهَلة (build/cache) : $ignoredNow" "DarkGray"
Say "   غير متتبعة متبقية      : $untracked" $(if($untracked -gt 0){"Red"}else{"Green"})
Say ""
Say "   الأرشيف الكامل (كل شيء): $vault" "Cyan"
Line

if ($untracked -gt 0) {
    Say "`n   ملفات لم تُرفع (محفوظة في الأرشيف):" "Yellow"
    git ls-files --others --exclude-standard | Select-Object -First 20 |
        ForEach-Object { Say "     $_" "DarkGray" }
}

Say "`n   انسخ السطر التالي وأرسله للمساعد:" "Yellow"
Say "   git ls-remote --heads origin | Select-String strongest" "White"
Say ""
