$content = Get-Content red-app/build.gradle.kts -Raw
$idx = $content.IndexOf('core:models')
if ($idx -ge 0) {
    $start = [Math]::Max(0, $idx - 20)
    $substr = $content.Substring($start, 40)
    "Found at index $($idx): $($substr)"
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($content.Substring($idx - 5, 30))
    $hex = ($bytes | ForEach-Object { '{0:X2}' -f $_ }) -join ' '
    "Hex: $($hex)"
}