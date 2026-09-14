# Dinstar UC2000 Web Session Helper
# Usage: .\scripts\dinstar-web-login.ps1 -Action login|portinfo|sipget
param([string]$Action = "login")

# تحذير أمني (audit 2026-09-10): أي تفريغ جلسة (dinstar_cookies.txt /
# dinstar_jar.txt / *.htm) سرّ محلي — مغطى بـ .gitignore فلا تلتزمه أبدًا.
Write-Warning "Session dumps (dinstar_cookies.txt/dinstar_jar.txt/*.htm) are local secrets covered by .gitignore — never commit them."

add-type @"
using System.Net;
using System.Security.Cryptography.X509Certificates;
public class TrustAllCerts : ICertificatePolicy {
    public bool CheckValidationResult(ServicePoint s, X509Certificate c, WebRequest r, int p) { return true; }
}
"@
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
[Net.ServicePointManager]::CertificatePolicy = New-Object TrustAllCerts

$base = "https://192.168.11.1"
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession

# 1. Login
try {
    $loginBody = "username=admin&password=admin123"
    $r = Invoke-WebRequest -Uri "$base/goform/IADIdentityAuth" -Method POST `
        -Body $loginBody -ContentType "application/x-www-form-urlencoded" `
        -WebSession $session -MaximumRedirection 5 -TimeoutSec 20
    Write-Output "LOGIN_HTTP:$($r.StatusCode)"
} catch {
    Write-Output ("LOGIN_ERR:" + $_.Exception.Message)
}
Write-Output "=== COOKIES ==="
$session.Cookies.GetCookies($base + "/") | ForEach-Object {
    Write-Output ("COOKIE:" + $_.Name + "=" + $_.Value)
}

if ($Action -eq "portinfo") {
    try {
        $r2 = Invoke-WebRequest -Uri "$base/WebGetPortInfoAll" -WebSession $session -TimeoutSec 20
        Write-Output ("PORTINFO_LEN:" + $r2.Content.Length)
        Write-Output ($r2.Content.Substring(0, [Math]::Min(2000, $r2.Content.Length)))
    } catch {
        Write-Output ("PORTINFO_ERR:" + $_.Exception.Message)
    }
}

# Save session cookies for reuse
$session.Cookies.GetCookies($base + "/") | ForEach-Object {
    Write-Output ("EXPORT:" + $_.Name + ":" + $_.Value)
}
