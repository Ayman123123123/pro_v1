# start-announcer.ps1 - keeps the YOUNES LAN announcer alive (auto-restart loop)
$log = Join-Path $PSScriptRoot 'announcer.log'
$py  = 'python'
while ($true) {
    "===== restart $(Get-Date) =====" | Out-File $log -Append -Encoding utf8
    & $py (Join-Path $PSScriptRoot 'server-announcer.py') *>> $log
    "exited code $LASTEXITCODE - restarting in 5s" | Out-File $log -Append -Encoding utf8
    Start-Sleep -Seconds 5
    # keep the log from growing forever
    if ((Get-Item $log -ErrorAction SilentlyContinue).Length -gt 1MB) {
        Get-Content $log -Tail 200 | Out-File $log -Encoding utf8
    }
}