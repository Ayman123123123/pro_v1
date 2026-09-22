<#
.SYNOPSIS
    يضبط ساعة بوابة DINSTAR على الزمن الحقيقي بمنطقة الجهاز.

.DESCRIPTION
    ── المشكلة ──────────────────────────────────────────────────────────
    ساعة البوابة تنزلق بلا حدّ لأن NTP لا يصل: الجهاز على شبكة إدارة معزولة
    (‎192.168.11.0/24) بلا مسار إلى `0.pool.ntp.org`. القياس الحيّ
    2026-09-06 من ترويسة `Date` في ردّ الجهاز:

        ساعة الجهاز : Sat Sep  5 03:04:01 2026
        الحقيقي(+03): Sun Sep  6 04:32:35 2026
        الانزلاق    : 25.5 ساعة إلى الخلف

    أثر ذلك كان صامتًا تمامًا:
      • `get_cdr` مع `time_after=now-24h` يرد `cdr:[]` — ردّ **ناجح** لا
        استثناء، فلا يتفعّل أي تدرّج احتياطي، ويبقى `dinstar_cdr` فارغًا.
      • كل زمن يصل من الجهاز (CDR، SMS الوارد، تقارير التسليم) مؤرَّخ
        بالماضي، فترتيب المحادثات والتحليلات مبنيّ على زمن خاطئ.

    ── الحل ─────────────────────────────────────────────────────────────
    البرنامج الثابت يكشف `POST /SyncTime` (تستخدمه واجهة الويب في زرّ
    "Set" داخل enManageCfg.htm). نُرسل الزمن الحقيقي بصيغة الجهاز نفسها.

    ⚠️ الشهر عند JavaScript ذو أساس صفري (`d.getMonth()` يعيد 0..11)،
    والبرنامج الثابت يتوقّع ذلك بالضبط لأنه بُني لتلك الواجهة. إرسال
    الشهر بأساس واحد يقدّم الساعة شهرًا كاملًا.

    الاعتماد الدائم يبقى NTP: يُفضَّل تزويد الجهاز بخادم NTP يصل إليه على
    شبكة الإدارة (مثل عنوان المضيف نفسه) بدل pool.ntp.org غير القابل
    للوصول. هذا السكربت يُصلح الحالة الآن ويمكن جدولته.

.PARAMETER GatewayHost
    عنوان البوابة على شبكة الإدارة.

.PARAMETER DeviceOffsetHours
    فرق منطقة الجهاز عن UTC. الافتراضي 3 (اليمن، ثابت بلا توقيت صيفي)
    ويطابق `P10=Etc/GMT-3` المضبوط على الجهاز.

.PARAMETER MaxSkewSeconds
    لا يُكتب شيء إن كان الانزلاق أصغر من هذا. يمنع كتابة بلا داعٍ عند
    الجدولة الدورية.

.EXAMPLE
    .\sync-dinstar-clock.ps1
    .\sync-dinstar-clock.ps1 -MaxSkewSeconds 0   # اكتب دائمًا
#>
[CmdletBinding()]
param(
    [string]$GatewayHost = '192.168.11.1',
    [int]$DeviceOffsetHours = 3,
    [int]$MaxSkewSeconds = 120,
    [string]$Username = 'admin',
    [string]$EnvFile
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$scriptDir = Split-Path -Parent $PSCommandPath
if (-not $EnvFile) {
    $EnvFile = Join-Path $scriptDir '..\RED_Ultimate_V1-main\RED_Ultimate\.env'
}

function Get-EnvValue {
    param([string]$Path, [string]$Key)
    if (-not (Test-Path -LiteralPath $Path)) { throw "لم يُعثر على ملف البيئة: $Path" }
    $line = Select-String -LiteralPath $Path -Pattern "^$Key=" | Select-Object -First 1
    if (-not $line) { throw "المفتاح $Key غير موجود في $Path" }
    return $line.Line.Substring($Key.Length + 1)
}

$password = Get-EnvValue -Path $EnvFile -Key 'DINSTAR_PASSWORD'
$base = "https://$GatewayHost"

if (-not ('DinstarClockTrustAll' -as [type])) {
    Add-Type @'
using System.Net;
using System.Security.Cryptography.X509Certificates;
public class DinstarClockTrustAll : ICertificatePolicy {
    public bool CheckValidationResult(ServicePoint sp, X509Certificate cert, WebRequest req, int problem) { return true; }
}
'@
}
[System.Net.ServicePointManager]::CertificatePolicy = New-Object DinstarClockTrustAll
[System.Net.ServicePointManager]::SecurityProtocol = 'Tls12,Tls11,Tls'

# ── قياس الانزلاق من ترويسة Date قبل أي كتابة ────────────────────────
# الترويسة تأتي على ردّ 401 أيضًا، فلا تحتاج جلسة.
$deviceClock = $null
try {
    Invoke-WebRequest -Uri "$base/api/get_cdr" -Method POST -Body '{}' `
        -ContentType 'application/json' -TimeoutSec 12 -UseBasicParsing | Out-Null
} catch {
    $resp = $_.Exception.Response
    if ($resp) { $deviceClock = $resp.Headers['Date'] }
}

$targetLocal = [DateTime]::UtcNow.AddHours($DeviceOffsetHours)
Write-Host "[clock] device header : $deviceClock"
Write-Host "[clock] target (UTC+$DeviceOffsetHours) : $($targetLocal.ToString('ddd MMM dd HH:mm:ss yyyy'))"

if ($deviceClock) {
    # صيغة الجهاز: `Sat Sep  5 03:04:01 2026` (asctime، غير قياسية لـHTTP)
    $normalized = ($deviceClock -replace '\s+', ' ').Trim()
    $parsed = [DateTime]::MinValue
    $ok = [DateTime]::TryParseExact(
        $normalized, 'ddd MMM d HH:mm:ss yyyy',
        [Globalization.CultureInfo]::InvariantCulture,
        [Globalization.DateTimeStyles]::None, [ref]$parsed
    )
    if ($ok) {
        $skew = [math]::Abs(($targetLocal - $parsed).TotalSeconds)
        Write-Host ("[clock] skew : {0:N0} seconds ({1:N2} hours)" -f $skew, ($skew / 3600))
        if ($skew -lt $MaxSkewSeconds) {
            Write-Host "[clock] الانزلاق أصغر من $MaxSkewSeconds ثانية — لا كتابة." -ForegroundColor Green
            return
        }
    } else {
        Write-Host "[clock] تعذّر تحليل ترويسة الوقت — سيُكتب الزمن على أي حال." -ForegroundColor Yellow
    }
}

# ── جلسة واجهة الويب: /SyncTime يستعمل devckie لا Digest ─────────────
$session = $null
$loginBody = "username=$([uri]::EscapeDataString($Username))&password=$([uri]::EscapeDataString($password))"
Invoke-WebRequest -Uri "$base/goform/IADIdentityAuth" -Method POST -Body $loginBody `
    -ContentType 'application/x-www-form-urlencoded' -SessionVariable session `
    -TimeoutSec 20 -UseBasicParsing | Out-Null

# ⚠️ month بأساس صفري — نسخة طبق الأصل من clickSyncTime() في enManageCfg.htm
$epochSeconds = [long][Math]::Floor(([DateTimeOffset]::UtcNow).ToUnixTimeMilliseconds() / 1000)
$payload = [ordered]@{
    time        = $epochSeconds
    year        = $targetLocal.Year
    month       = $targetLocal.Month - 1
    date        = $targetLocal.Day
    hour        = $targetLocal.Hour
    minute      = $targetLocal.Minute
    second      = $targetLocal.Second
    millisecond = $targetLocal.Millisecond
} | ConvertTo-Json -Compress

$r = Invoke-WebRequest -Uri "$base/SyncTime" -Method POST -Body $payload `
    -ContentType 'application/json' -WebSession $session -TimeoutSec 20 -UseBasicParsing
Write-Host "[clock] POST /SyncTime => HTTP $($r.StatusCode)"

# ── التحقق: نقرأ ساعة الجهاز من جديد ────────────────────────────────
Start-Sleep -Seconds 3
$after = $null
try {
    Invoke-WebRequest -Uri "$base/api/get_cdr" -Method POST -Body '{}' `
        -ContentType 'application/json' -TimeoutSec 12 -UseBasicParsing | Out-Null
} catch {
    $resp2 = $_.Exception.Response
    if ($resp2) { $after = $resp2.Headers['Date'] }
}
Write-Host "[clock] device header after : $after"

$expected = [DateTime]::UtcNow.AddHours($DeviceOffsetHours)
$normalizedAfter = if ($after) { ($after -replace '\s+', ' ').Trim() } else { '' }
$parsedAfter = [DateTime]::MinValue
$okAfter = [DateTime]::TryParseExact(
    $normalizedAfter, 'ddd MMM d HH:mm:ss yyyy',
    [Globalization.CultureInfo]::InvariantCulture,
    [Globalization.DateTimeStyles]::None, [ref]$parsedAfter
)
if (-not $okAfter) {
    Write-Host "[WARN] تعذّر التحقق من ساعة الجهاز بعد الضبط." -ForegroundColor Yellow
    exit 2
}
$residual = [math]::Abs(($expected - $parsedAfter).TotalSeconds)
if ($residual -le 90) {
    Write-Host ("[OK] ساعة البوابة مضبوطة (فرق متبقٍّ {0:N0} ثانية)." -f $residual) -ForegroundColor Green
} else {
    Write-Host ("[FAIL] الفرق ما زال {0:N0} ثانية — راجع TimeZone على enManageCfg.htm." -f $residual) -ForegroundColor Red
    exit 1
}
