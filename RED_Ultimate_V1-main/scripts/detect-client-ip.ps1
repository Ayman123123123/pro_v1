<#
.SYNOPSIS
    ????? ????? IP ??????? ?????? (hotspot) ?????? ??? .env.
.PARAMETER EnvFile
    ???? ??? .env. ????????? ???? RED_Ultimate\.env.
.PARAMETER DryRun
    ???? ?? ????? ??? ?????.
#>
[CmdletBinding()]
param(
    [string]$EnvFile = "$PSScriptRoot\..\RED_Ultimate_V1-main\RED_Ultimate\.env",
    [switch]$DryRun
)
$ErrorActionPreference = 'Stop'
$wifi = Get-NetAdapter -Name '*Wi-Fi*' -ErrorAction SilentlyContinue | Where-Object { $_.Status -eq 'Up' } | Select-Object -First 1
if (-not $wifi) { Write-Host "[skip] ?? Wi-Fi ???" -ForegroundColor Yellow; exit 0 }
$iface = $wifi.InterfaceAlias
$ip = Get-NetIPAddress -InterfaceAlias $iface -AddressFamily IPv4 -ErrorAction SilentlyContinue | Where-Object { $_.PrefixOrigin -eq 'Dhcp' } | Select-Object -ExpandProperty IPAddress | Select-Object -First 1
if (-not $ip) { Write-Host "[skip] ?? DHCP ??? $iface" -ForegroundColor Yellow; exit 0 }
Write-Host "[detect] $iface = $ip"
if (-not (Test-Path -LiteralPath $EnvFile)) { Write-Host "[error] not found: $EnvFile" -ForegroundColor Red; exit 1 }
$content = Get-Content -LiteralPath $EnvFile -Raw
$oldClient = (($content | Select-String '^CLIENT_LAN_IP=').Matches.Value -split '=')[1]
if ($oldClient -eq $ip) { Write-Host "[ok] .env ??? $ip" -ForegroundColor Green; exit 0 }
Write-Host "[plan] $oldClient -> $ip"
if ($DryRun) { exit 0 }
$new = $content -replace [regex]::Escape($oldClient), $ip
Set-Content -LiteralPath $EnvFile -Value $new -NoNewline -Encoding UTF8
Write-Host "[write] ??" -ForegroundColor Green