<#
.SYNOPSIS
  فحص سريع لواجهة DINSTAR UC2000-VE عبر HTTP/JSON.

.DESCRIPTION
  يتحقّق من مصفوفة الاستدعاء الصحيحة مقابل جهاز حيّ:
   1. `--anyauth` بدل فرض `--digest`: يترك curl يختار Basic/Digest من التحدّي.
   2. `get_status` و`get_cdr` بـPOST وجسم JSON — الموثّق في دليل الواجهة.
   3. `get_port_info` بـGET ومعاملات استعلام (POST عليه يُعيد 411/000).
   4. سقوط إلى HTTP:80 إن تعذّر HTTPS:443 (بعض الإصدارات).

  آمن على جهاز في الخدمة: قراءة فقط، بلا تغيير حالة.

  ### أعطال أُصلحت في هذا الملف
  النسخة الأولى لم تكن تُعرَب في PowerShell 5.1 أصلًا — أي أنها لم تُشغَّل قطّ:
   * استدعاء أمر كقيمة في `@{...}` بلا `$(...)`: يُعرَب في وضع التعبير، فيصير
     `&` في الرابط عاملًا محجوزًا ويسقط الملف بخطأ نحوي.
   * نصّ مشوَّه الترميز (mojibake) أدخل `"` داخل نصّ مقتبس فقطع نهايته.
     الملف كان غير مُتعقَّب فأفلت من إصلاح الترميز الشامل.
   * `$args` متغيّر تلقائي في PowerShell — إسناده يصطدم بوسائط الدالة.
   * `-w '`nHTTP=...'` يُمرِّر محرفَين حرفيَّين إلى curl لا سطرًا جديدًا؛
     الصحيح `\n` لأن curl هو مَن يُفسِّرها.
   * الافتراضي 192.168.11.1 لا وجود له على هذه الشبكة (البوابات .2 و.3).

.PARAMETER Ip
  عنوان البوابة. الافتراضي 192.168.11.2 (UC2000-VE-8G).

.PARAMETER User
  مستخدم الإدارة. الافتراضي 'admin'.

.PARAMETER Pass
  كلمة المرور. لا افتراضي: تُمرَّر صراحةً حتى لا تُخزَّن في الملف.

.EXAMPLE
  powershell -File ./scripts/dinstar_api_smoke.ps1 -Pass 'secret'
  powershell -File ./scripts/dinstar_api_smoke.ps1 -Ip 192.168.11.3 -Pass 'secret'
#>
[CmdletBinding()]
param(
  [string]$Ip = '192.168.11.2',
  [string]$User = 'admin',
  [Parameter(Mandatory = $true)][string]$Pass,
  [int]$TimeoutSec = 12
)

$ErrorActionPreference = 'Continue'
$ProgressPreference = 'SilentlyContinue'

function Invoke-Probe {
  param(
    [string]$Label,
    [string]$Url,
    [string]$Method = 'GET',
    [string]$Body = $null
  )

  # لا يُسمّى $args: ذاك متغيّر تلقائي يحمل وسائط الدالة نفسها.
  $curlArgs = @(
    '-sk', '--anyauth', '-u', "${User}:${Pass}",
    '--max-time', "$TimeoutSec",
    # \n لا `n: curl هو مَن يُفسِّر الهروب في قالب -w.
    '-w', '\nHTTP=%{http_code}'
  )
  if ($Method -eq 'POST') {
    $curlArgs += @('-X', 'POST', '-H', 'Content-Type: application/json')
    if ($Body) { $curlArgs += @('-d', $Body) }
  }
  $curlArgs += $Url

  $out = (& curl.exe @curlArgs 2>&1) -join "`n"
  $code = if ($out -match 'HTTP=(\d+)') { [int]$Matches[1] } else { 0 }
  $body = ($out -replace 'HTTP=\d+\s*$', '').Trim()
  $firstLine = (($body -split "`n") | Select-Object -First 1).Trim()
  if ($firstLine.Length -gt 140) { $firstLine = $firstLine.Substring(0, 140) + '...' }

  Write-Host ("[{0,-34}] HTTP={1,-4} {2}" -f $Label, $code, $firstLine)
  return $code
}

Write-Host ""
Write-Host "=== DINSTAR API SMOKE TEST - $Ip ==="
Write-Host ""

# استدعاء الأمر داخل @{} يلزمه $(...): بدونه يُعرَب الرابط في وضع التعبير
# فيصير & عاملًا محجوزًا ويسقط الملف كله بخطأ نحوي قبل التشغيل.
$results = @(
  [pscustomobject]@{
    Test = 'get_port_info (GET, HTTPS)'
    Code = $(Invoke-Probe 'get_port_info/HTTPS' "https://$Ip/api/get_port_info?port=0&info_type=slot,callstate,signal")
  }
  [pscustomobject]@{
    Test = 'get_port_info (GET, HTTP:80)'
    Code = $(Invoke-Probe 'get_port_info/HTTP' "http://$Ip/api/get_port_info?port=0&info_type=slot,callstate,signal")
  }
  [pscustomobject]@{
    Test = 'get_status (POST ["performance"])'
    Code = $(Invoke-Probe 'get_status/POST' "https://$Ip/api/get_status" -Method POST -Body '["performance"]')
  }
  [pscustomobject]@{
    Test = 'get_cdr (POST {"port":[0..7]})'
    Code = $(Invoke-Probe 'get_cdr/POST' "https://$Ip/api/get_cdr" -Method POST -Body '{"port":[0,1,2,3,4,5,6,7]}')
  }
)

Write-Host ""
Write-Host "--- Summary ---"
$results | Format-Table -AutoSize

$ok = ($results | Where-Object { $_.Code -in 200, 202 }).Count
$authReachable = ($results | Where-Object { $_.Code -in 200, 202, 401, 403 }).Count
Write-Host "Successful (200/202):              $ok / $($results.Count)"
Write-Host "Auth-reachable (200/202/401/403):  $authReachable / $($results.Count)"
Write-Host ""

if ($ok -eq 0 -and $authReachable -eq $results.Count) {
  Write-Host "المسارات مُعرَّفة لكن الاعتماد مرفوض." -ForegroundColor Yellow
  Write-Host "  راجع كلمة مرور الإدارة في System Configuration > Setting." -ForegroundColor Yellow
} elseif ($ok -gt 0) {
  Write-Host "نجح استدعاء واحد على الأقل: المسار والمصادقة صحيحان." -ForegroundColor Green
} else {
  Write-Host "لا استجابة: تحقّق من الوصول الشبكي إلى $Ip." -ForegroundColor Red
}

# رمز الخروج يُميّز «لا وصول» عن «وصول بلا نجاح»: يُستهلك في التشغيل الآلي.
exit $(if ($ok -gt 0) { 0 } else { 1 })
