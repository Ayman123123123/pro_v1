$ErrorActionPreference = 'Stop'
$inputFile = 'C:\Users\hpc01\Pictures\pro_new\DINSTAR_CONFIG_BACKUP_BEFORE_NUMBER_LEARNING_2026-08-23.cfg'
$outputFile = 'C:\Users\hpc01\Pictures\pro_new\DINSTAR_CONFIG_BACKUP_RELEVANT_SANITIZED_2026-08-23.txt'
$patterns = 'HBPhone|PhoneNumber|NumberLearn|SIP|PJSIP|RTP|PortGroup|Route|Network|WIA'
$lines = Get-Content -LiteralPath $inputFile | Where-Object { $_ -match $patterns }
$sanitized = $lines | ForEach-Object {
    $_ -replace '(?i)(password|passwd|secret|auth|key)(\s*[=:]\s*)([^,;\s]+)', '$1$2***REDACTED***' `
       -replace '(?i)(username|user)(\s*[=:]\s*)([^,;\s]+)', '$1$2***REDACTED***'
}
$sanitized | Set-Content -LiteralPath $outputFile -Encoding UTF8
Write-Output ("SANITIZED_FILE={0}" -f $outputFile)
Write-Output ("MATCHED_LINES={0}" -f @($sanitized).Count)
$sanitized
