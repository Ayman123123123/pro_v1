$ErrorActionPreference = 'Stop'
$root = 'C:\Users\hpc01\Pictures\pro_new\RED_Ultimate_V1-main\RED_Ultimate\backend-server'
$outFile = 'C:\Users\hpc01\Pictures\pro_new\PSTN_BACKEND_ROUTING_INDEX_2026-08-23.txt'
$matches = Get-ChildItem -LiteralPath $root -Recurse -File -Include *.kt,*.java,*.yml,*.yaml,*.properties |
    Select-String -Pattern 'RED_SIM_NUMBER|RED_GW|from-red-backend|OriginateAction|RedirectAction|PstnManager|PSTN|AmiClient' |
    ForEach-Object { '{0}:{1}: {2}' -f $_.Path.Replace($root,''), $_.LineNumber, ($_.Line -replace '(?i)(password|secret|token)(\s*[=:]\s*)([^\s,]+)', '$1$2***REDACTED***') }
if (@($matches).Count -eq 0) { $matches = @('# No relevant PSTN routing symbols found.') }
Set-Content -LiteralPath $outFile -Value $matches -Encoding UTF8
Write-Output ("INDEX_FILE={0}" -f $outFile)
Write-Output ("MATCH_COUNT={0}" -f @($matches).Count)
