param(
  [switch]$Apply
)
if (-not $Apply) {
    "Dry-run only - pass -Apply to write red-app/build.gradle.kts"
    exit 0
}
$content = Get-Content red-app/build.gradle.kts -Raw
$content = $content -replace 'implementation\(project\(":core:models"\)', 'implementation(project(":core:models"))'
$content = $content -replace 'implementation\(project\(":core:network"\)', 'implementation(project(":core:network"))'
$content = $content -replace 'implementation\(project\(":feature:camera"\)', 'implementation(project(":feature:camera"))'
$content | Set-Content red-app/build.gradle.kts
"Fixed missing closing parentheses"