# Dinstar UC2000 SIP Config Updater
# Sets SessionTimerCfg=1 and Sip100relEnable=1, preserves all other fields
# Usage: .\scripts\dinstar-sip-update.ps1

$html = Get-Content sipcfg.html -Raw
$fields = @{}
$matches = [regex]::Matches($html, '<input[^>]*name="([^"]+)"[^>]*>')
foreach ($m in $matches) {
    $tag = $m.Groups[0].Value
    $name = $m.Groups[1].Value
    if ($tag -match 'type="(radio|checkbox)"') {
        if ($tag -match 'checked') {
            $v = [regex]::Match($tag, 'value="([^"]*)"')
            $fields[$name] = $v.Groups[1].Value
        }
    } elseif ($tag -notmatch 'type="(submit|button|reset|file)"') {
        if (-not $fields.ContainsKey($name)) {
            $v = [regex]::Match($tag, 'value="([^"]*)"')
            $fields[$name] = $v.Groups[1].Value
        }
    }
}
$selMatches = [regex]::Matches($html, '<select[^>]*name="([^"]+)"[^>]*>(.*?)</select>', 'Singleline')
foreach ($m in $selMatches) {
    $name = $m.Groups[1].Value
    $inner = $m.Groups[2].Value
    $sel = [regex]::Match($inner, '<option[^>]*value="([^"]*)"[^>]*selected')
    if ($sel.Success) { $fields[$name] = $sel.Groups[1].Value }
}

# Apply our two changes
$fields['SessionTimerCfg'] = '1'
$fields['Sip100relEnable'] = '1'

# URL-encode using Uri class (always available)
$parts = @()
foreach ($k in $fields.Keys) {
    $ek = [System.Uri]::EscapeDataString($k)
    $ev = [System.Uri]::EscapeDataString($fields[$k])
    $parts += "$ek=$ev"
}
$body = $parts -join '&'
$body | Out-File -FilePath sipcfg_post.txt -Encoding ASCII -NoNewline
Write-Output ("FIELDS:" + $fields.Count + " BYTES:" + $body.Length)
Write-Output ("SessionTimerCfg=" + $fields['SessionTimerCfg'])
Write-Output ("Sip100relEnable=" + $fields['Sip100relEnable'])
Write-Output ("SipPxyIP=" + $fields['SipPxyIP'])
