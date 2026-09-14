<#
.SYNOPSIS
    يوائم جدولَي التوجيه على بوابة DINSTAR UC2000 مع الحقيقة الفيزيائية للشرائح.

.DESCRIPTION
    ── المشكلة التي يحلّها ──────────────────────────────────────────────
    جدول IP→Tel على الجهاز الحيّ كان يحمل قواعد «متقدّمة» (advanced_mode=1)
    تضع **ICCID الشريحة** في `src_prefix` و`dest_prefix`:

        idx=5 dest=5 adv=1 srcpfx='89967110006038113245' destpfx='...245'

    وهذان الحقلان بادئتا **رقم المتصل** و**الرقم المطلوب** لا معرّف شريحة.
    فلا مكالمة في الوجود تبدأ بـ`8996711...`، أي أن القاعدة لا تُطابق أبدًا،
    فتسقط المكالمة إلى القاعدة الشاملة idx=63 التي توجّه إلى **مجموعة
    منافذ** لا إلى منفذ بعينه — فيختار الجهاز منفذًا بنفسه ويضيع الربط
    الدائم 1:1 بين المستخدم وشريحته. وفي الاتجاه المعاكس ظهر
    `reason='FORBID CALL'` على المنفذ 7 لأن الوارد لم يجد قاعدة تسمح له.

    والأسوأ: المنافذ 0..4 فقدت قواعد IP→Tel كليًّا (الجدول الحيّ يحمل 5,6,7
    و63 فقط)، فمكالمات تلك المنافذ لا مسار لها إطلاقًا.

    ── الحل ─────────────────────────────────────────────────────────────
    قاعدة بسيطة صريحة لكل منفذ في الاتجاهين، بلا بوادئ وبلا أنماط متقدّمة:

        IP→Tel : من الجذع (src_mode=2, src=31) → المنفذ N     (dest_mode=0)
        Tel→IP : من المنفذ N (src_mode=0)      → الجذع 31      (dest_mode=2)

    الجهاز يختار المنفذ بمقبس الوجهة الذي وصل إليه INVITE (5061..5067،
    والمنفذ 0 على 5068)، والقاعدة الصريحة تُثبّت ذلك الاختيار بدل تركه
    لمجموعة المنافذ. القاعدة الشاملة idx=63 تبقى كما هي للتوافق.

    ثوابت الجهاز (من enRouteIP2PSTNList.htm الحيّة):
        src/dest mode: PORT=0, PORT_GROUP=1, IP=2, IP_GROUP=3, SIP_SERVER=4, ANY=255
        operation:     Allow=1, Forbid=2, Callback=16, IVR=32, Ignore=64
        src=31 هو فهرس جذع الـIP الذي يشير إلى 192.168.11.10:5060 (P2731).

.PARAMETER GatewayHost
    عنوان البوابة على شبكة الإدارة.

.PARAMETER Ports
    المنافذ المشمولة. الافتراضي 0..7.

.PARAMETER TrunkIndex
    فهرس جذع الـIP في جدول الجهاز (`src`/`dest` عند mode=2). الافتراضي 31.

.PARAMETER Apply
    بلاها يعمل السكربت بوضع القراءة فقط: يعرض الفرق ولا يكتب شيئًا.

.EXAMPLE
    .\repair-dinstar-routing.ps1                     # معاينة الفرق
    .\repair-dinstar-routing.ps1 -Apply              # تطبيق بعد نسخة احتياطية
#>
[CmdletBinding()]
param(
    [string]$GatewayHost = '192.168.11.1',
    [int[]]$Ports = @(0, 1, 2, 3, 4, 5, 6, 7),
    [int]$TrunkIndex = 31,
    [switch]$Apply,
    [string]$Username = 'admin',
    [string]$EnvFile
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

# $PSScriptRoot ليس متاحًا في قيم param الافتراضية على Windows PowerShell 5.1،
# فيُحسب المسار هنا من موقع السكربت نفسه.
$scriptDir = Split-Path -Parent $PSCommandPath
if (-not $EnvFile) {
    $EnvFile = Join-Path $scriptDir '..\RED_Ultimate_V1-main\RED_Ultimate\.env'
}

# ── اعتماد الدخول من .env — لا كلمات مرور مضمّنة في السكربت ───────────
function Get-EnvValue {
    param([string]$Path, [string]$Key)
    if (-not (Test-Path -LiteralPath $Path)) { throw "لم يُعثر على ملف البيئة: $Path" }
    $line = Select-String -LiteralPath $Path -Pattern "^$Key=" | Select-Object -First 1
    if (-not $line) { throw "المفتاح $Key غير موجود في $Path" }
    return $line.Line.Substring($Key.Length + 1)
}

$password = Get-EnvValue -Path $EnvFile -Key 'DINSTAR_PASSWORD'
$base = "https://$GatewayHost"

# ── شهادة الجهاز موقّعة ذاتيًا على شبكة إدارة خاصة ────────────────────
if (-not ('DinstarTrustAll' -as [type])) {
    Add-Type @'
using System.Net;
using System.Security.Cryptography.X509Certificates;
public class DinstarTrustAll : ICertificatePolicy {
    public bool CheckValidationResult(ServicePoint sp, X509Certificate cert, WebRequest req, int problem) { return true; }
}
'@
}
[System.Net.ServicePointManager]::CertificatePolicy = New-Object DinstarTrustAll
[System.Net.ServicePointManager]::SecurityProtocol = 'Tls12,Tls11,Tls'

# ── جلسة واجهة الويب (devckie) — مسارات /SetRouting* تستعملها لا Digest ─
$session = $null
$loginBody = "username=$([uri]::EscapeDataString($Username))&password=$([uri]::EscapeDataString($password))"
Invoke-WebRequest -Uri "$base/goform/IADIdentityAuth" -Method POST -Body $loginBody `
    -ContentType 'application/x-www-form-urlencoded' -SessionVariable session `
    -TimeoutSec 20 -UseBasicParsing | Out-Null
Write-Host "[login] session established with $GatewayHost"

function Get-RoutingTable {
    param([ValidateSet('ip-to-tel', 'tel-to-ip')][string]$Type)
    $r = Invoke-WebRequest -Uri "$base/GetRoutingList?routing_type=$Type" -WebSession $session `
        -TimeoutSec 20 -UseBasicParsing
    return ($r.Content | ConvertFrom-Json).routing
}

function Show-Table {
    param([string]$Label, $Rows)
    Write-Host "  $Label ($(@($Rows).Count) rule(s)):"
    foreach ($x in $Rows) {
        Write-Host ("    idx={0,-2} en={1} src_mode={2} src={3,-3} dest_mode={4} dest={5,-3} adv={6} srcpfx='{7}' destpfx='{8}' op={9} desc='{10}'" -f `
                $x.index, $x.enable, $x.src_mode, $x.src, $x.dest_mode, $x.dest, $x.advanced_mode, $x.src_prefix, $x.dest_prefix, $x.operation, $x.desc)
    }
}

# ── حالة ما قبل التغيير: نسخة احتياطية على القرص دائمًا ───────────────
$stamp = Get-Date -Format 'yyyy-MM-dd_HH-mm-ss'
$backupDir = Join-Path $scriptDir "..\docs\dinstar-tools\routing-repair-$stamp"
New-Item -ItemType Directory -Path $backupDir -Force | Out-Null

$beforeIp2Tel = Get-RoutingTable -Type 'ip-to-tel'
$beforeTel2Ip = Get-RoutingTable -Type 'tel-to-ip'
($beforeIp2Tel | ConvertTo-Json -Depth 6) | Set-Content -LiteralPath "$backupDir\before-ip-to-tel.json" -Encoding UTF8
($beforeTel2Ip | ConvertTo-Json -Depth 6) | Set-Content -LiteralPath "$backupDir\before-tel-to-ip.json" -Encoding UTF8

Write-Host "`n=== BEFORE ==="
Show-Table -Label 'IP->Tel' -Rows $beforeIp2Tel
Show-Table -Label 'Tel->IP' -Rows $beforeTel2Ip
Write-Host "[backup] $backupDir"

# ── الجداول المستهدفة ────────────────────────────────────────────────
# `del_prefix=255` و`reserved_number=255` هما «بلا تعديل على الرقم» في
# صيغة الجهاز؛ 0 تعني «احذف صفر خانات» وهي ليست نفس الشيء في كل إصدار،
# فنستخدم 255 كما تفعل واجهة الويب للقواعد البسيطة.
function New-Rule {
    param(
        [int]$Index, [string]$Desc,
        [int]$SrcMode, [int]$Src,
        [int]$DestMode, [int]$Dest
    )
    return [ordered]@{
        enable          = 1
        desc            = $Desc
        src_mode        = $SrcMode
        src             = $Src
        dest_mode       = $DestMode
        dest            = $Dest
        advanced_mode   = 0
        src_prefix      = ''
        dest_prefix     = ''
        del_prefix      = 255
        add_prefix      = ''
        add_suffix      = ''
        reserved_number = 255
        operation       = 1      # Allow
        index           = $Index
    }
}

$MODE_PORT = 0
$MODE_IP = 2

$targetIp2Tel = @()
foreach ($p in $Ports) {
    $targetIp2Tel += New-Rule -Index $p -Desc "port-$p" -SrcMode $MODE_IP -Src $TrunkIndex -DestMode $MODE_PORT -Dest $p
}
# القاعدة الشاملة تبقى كما وجدناها بالضبط (لا نلمس idx=63).
$defaultIp2Tel = $beforeIp2Tel | Where-Object { $_.index -eq 63 }
foreach ($d in $defaultIp2Tel) {
    $targetIp2Tel += [ordered]@{
        enable = $d.enable; desc = $d.desc; src_mode = $d.src_mode; src = $d.src
        dest_mode = $d.dest_mode; dest = $d.dest; advanced_mode = $d.advanced_mode
        src_prefix = $d.src_prefix; dest_prefix = $d.dest_prefix; del_prefix = $d.del_prefix
        add_prefix = $d.add_prefix; add_suffix = $d.add_suffix
        reserved_number = $d.reserved_number; operation = $d.operation; index = $d.index
    }
}

$targetTel2Ip = @()
foreach ($p in $Ports) {
    $targetTel2Ip += New-Rule -Index $p -Desc "port$p-asterisk" -SrcMode $MODE_PORT -Src $p -DestMode $MODE_IP -Dest $TrunkIndex
}
$defaultTel2Ip = $beforeTel2Ip | Where-Object { $_.index -eq 63 }
foreach ($d in $defaultTel2Ip) {
    $targetTel2Ip += [ordered]@{
        enable = $d.enable; desc = $d.desc; src_mode = $d.src_mode; src = $d.src
        dest_mode = $d.dest_mode; dest = $d.dest; advanced_mode = $d.advanced_mode
        src_prefix = $d.src_prefix; dest_prefix = $d.dest_prefix; del_prefix = $d.del_prefix
        add_prefix = $d.add_prefix; add_suffix = $d.add_suffix
        reserved_number = $d.reserved_number; operation = $d.operation; index = $d.index
    }
}

Write-Host "`n=== TARGET ==="
Show-Table -Label 'IP->Tel' -Rows ($targetIp2Tel | ForEach-Object { [pscustomobject]$_ })
Show-Table -Label 'Tel->IP' -Rows ($targetTel2Ip | ForEach-Object { [pscustomobject]$_ })

if (-not $Apply) {
    Write-Host "`n[dry-run] لم يُكتب شيء. أعد التشغيل مع -Apply للتطبيق." -ForegroundColor Yellow
    return
}

# ── الكتابة ─────────────────────────────────────────────────────────
# واجهة الويب تُرسل الجدول **كاملًا** في POST واحد إلى /SetRouting*List،
# فالحقل المفقود يُحذف من الجهاز. لذلك نُرسل الجدول المبني أعلاه بتمامه.
function Set-RoutingTable {
    param([string]$Url, $Rows)
    $payload = @{ routing = @($Rows) } | ConvertTo-Json -Depth 6 -Compress
    $r = Invoke-WebRequest -Uri "$base/$Url" -Method POST -Body $payload `
        -ContentType 'application/json' -WebSession $session -TimeoutSec 30 -UseBasicParsing
    return $r.StatusCode
}

$c1 = Set-RoutingTable -Url 'SetRoutingIp2PstnList' -Rows $targetIp2Tel
Write-Host "[apply] SetRoutingIp2PstnList => HTTP $c1"
Start-Sleep -Seconds 2
$c2 = Set-RoutingTable -Url 'SetRoutingPstn2IpList' -Rows $targetTel2Ip
Write-Host "[apply] SetRoutingPstn2IpList => HTTP $c2"
Start-Sleep -Seconds 3

# ── التحقق: نقرأ من الجهاز لا من نيّتنا ─────────────────────────────
$afterIp2Tel = Get-RoutingTable -Type 'ip-to-tel'
$afterTel2Ip = Get-RoutingTable -Type 'tel-to-ip'
($afterIp2Tel | ConvertTo-Json -Depth 6) | Set-Content -LiteralPath "$backupDir\after-ip-to-tel.json" -Encoding UTF8
($afterTel2Ip | ConvertTo-Json -Depth 6) | Set-Content -LiteralPath "$backupDir\after-tel-to-ip.json" -Encoding UTF8

Write-Host "`n=== AFTER ==="
Show-Table -Label 'IP->Tel' -Rows $afterIp2Tel
Show-Table -Label 'Tel->IP' -Rows $afterTel2Ip

$missing = @()
foreach ($p in $Ports) {
    $a = $afterIp2Tel | Where-Object { $_.index -eq $p -and $_.dest_mode -eq $MODE_PORT -and $_.dest -eq $p -and $_.advanced_mode -eq 0 }
    if (-not $a) { $missing += "IP->Tel port $p" }
    $b = $afterTel2Ip | Where-Object { $_.index -eq $p -and $_.src_mode -eq $MODE_PORT -and $_.src -eq $p -and $_.advanced_mode -eq 0 }
    if (-not $b) { $missing += "Tel->IP port $p" }
}

if ($missing.Count -gt 0) {
    Write-Host "`n[FAIL] قواعد لم تُثبَّت على الجهاز:" -ForegroundColor Red
    $missing | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
    Write-Host "الاستعادة: أعد رفع before-*.json من $backupDir" -ForegroundColor Yellow
    exit 1
}

Write-Host "`n[OK] كل المنافذ ($($Ports -join ',')) لها قاعدة صريحة في الاتجاهين، بلا بوادئ ICCID." -ForegroundColor Green
Write-Host "[backup] $backupDir"
