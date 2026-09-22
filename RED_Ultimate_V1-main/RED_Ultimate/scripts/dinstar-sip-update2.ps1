# Dinstar SIP Config Updater v2 - excludes disabled fields (browser-accurate)
$html = Get-Content sipcfg.html -Raw
$fields = @{}
$matches = [regex]::Matches($html, '<input[^>]*name="([^"]+)"[^>]*>')
foreach ($m in $matches) {
    $tag = $m.Groups[0].Value
    $name = $m.Groups[1].Value
    # Skip disabled fields (browsers don't submit them)
    if ($tag -match 'disabled') { continue }
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
    $selTag = $m.Groups[0].Value
    if ($selTag -match 'disabled') { continue }
    $name = $m.Groups[1].Value
    $inner = $m.Groups[2].Value
    $sel = [regex]::Match($inner, '<option[^>]*value="([^"]*)"[^>]*selected')
    if ($sel.Success) { $fields[$name] = $sel.Groups[1].Value }
}
$fields['SessionTimerCfg'] = '1'
$fields['Sip100relEnable'] = '1'
$fields['ok'] = 'Save'
$parts = @()
foreach ($k in $fields.Keys) {
    $ek = [System.Uri]::EscapeDataString($k)
    $ev = [System.Uri]::EscapeDataString($fields[$k])
    $parts += "$ek=$ev"
}
$body = $parts -join '&'
$body | Out-File -FilePath sipcfg_post3.txt -Encoding ASCII -NoNewline
Write-Output ("FIELDS:" + $fields.Count + " BYTES:" + $body.Length)
Write-Output ("Has SipPxyIP: " + $fields.ContainsKey('SipPxyIP'))
