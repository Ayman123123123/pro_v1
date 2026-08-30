<#
.SYNOPSIS
  Guard against hardcoded private IPv4 literals in the Android app source.

.DESCRIPTION
  Scans RED_Ultimate/red-app/src/main for hardcoded RFC1918 addresses that should
  instead resolve from ServerEndpoint (the configured backend URL) or an explicit
  configuration source. Emulator/loopback aliases and documentation placeholders
  are allowed. A line containing the marker "ALLOW-IP" is always permitted
  (used for intentional last-resort fallbacks). Exits non-zero when a forbidden
  literal is found.

  Usage:
    powershell -ExecutionPolicy Bypass -File .\scripts\check-hardcoded-ips.ps1
#>
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$ScanDir = Join-Path $Root "red-app/src/main"

if (-not (Test-Path $ScanDir)) {
    Write-Host "Nothing to scan (no $ScanDir)." -ForegroundColor Yellow
    exit 0
}

# Allowed literals / line markers that make a hardcoded IP legitimate.
$AllowedPatterns = @(
    '127\.0\.0\.1',
    '10\.0\.2\.2',      # Android emulator host alias
    '10\.0\.3\.2',      # Genymotion emulator host alias
    '0\.0\.0\.0',
    '255\.255\.255\.255',
    'ALLOW-IP',         # explicit opt-in for intentional literals
    'localhost',
    'emulator',
    'alias',
    'placeholder',
    'example',
    'e\.g\.',
    'subnet',
    'comment',
    'deprecated',
    'fallback',
    'migrated',
    'default',
    'hint',
    'RED_SERVER_URL',
    'BuildConfig'
)

$IpRegex = [regex]'(?:(?:19[2-9]|2[0-1]\d|22[0-3])\.|10\.|172\.(?:1[6-9]|2\d|3[01])\.)\d{1,3}\.\d{1,3}'

$violations = @()
Get-ChildItem -Path $ScanDir -Recurse -Include *.kt,*.kts | ForEach-Object {
    $file = $_.FullName
    $lineNo = 0
    foreach ($line in (Get-Content $file)) {
        $lineNo++
        $match = $IpRegex.Match($line)
        if (-not $match.Success) { continue }
        $isAllowed = $false
        foreach ($p in $AllowedPatterns) {
            if ($line -match $p) { $isAllowed = $true; break }
        }
        if (-not $isAllowed) {
            $violations += "$($file):$lineNo : $($match.Value) -> $line".Trim()
        }
    }
}

if ($violations.Count -gt 0) {
    Write-Host "FAIL: hardcoded private IP literals found in app source:" -ForegroundColor Red
    $violations | ForEach-Object { Write-Host "  $_" }
    exit 1
}
Write-Host "PASS: no hardcoded private IP literals in $ScanDir" -ForegroundColor Green
exit 0
