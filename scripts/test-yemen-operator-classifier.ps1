# سكربت اختبار كاشف المشغلين اليمنيين — التحقق من كافة البادئات الجديدة
# متوافق مع YemeniOperatorDetector (Android) و YemenNumberPlan (Backend)

$TestNumbers = @(
    @{ Number = "712064924"; Expected = "Sabafon" },
    @{ Number = "733456789"; Expected = "YOU" },
    @{ Number = "777123456"; Expected = "YemenMobile" },
    @{ Number = "780123456"; Expected = "YemenMobile" },
    @{ Number = "701234567"; Expected = "YTelecom" },
    @{ Number = "101234567"; Expected = "Yemen4G" },
    @{ Number = "01234567";  Expected = "Fixed-Sanaa" },
    @{ Number = "02234567";  Expected = "Fixed-Aden" }
)

Write-Host "--- Starting Yemen Operator Classifier Test ---" -ForegroundColor Cyan

foreach ($test in $TestNumbers) {
    $num = $test.Number
    $expected = $test.Expected

    # محاكاة منطق Regex المستخدم في المشروع
    $detected = switch -Regex ($num) {
        "^71" { "Sabafon" }
        "^73" { "YOU" }
        "^77|^78" { "YemenMobile" }
        "^70" { "YTelecom" }
        "^10" { "Yemen4G" }
        "^01" { "Fixed-Sanaa" }
        "^02" { "Fixed-Aden" }
        Default { "Unknown" }
    }

    if ($detected -eq $expected) {
        Write-Host "[PASS] $num -> $detected" -ForegroundColor Green
    } else {
        Write-Host "[FAIL] $num -> Expected $expected, but got $detected" -ForegroundColor Red
    }
}

Write-Host "--- Test Complete ---" -ForegroundColor Cyan
