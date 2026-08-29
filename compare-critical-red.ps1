$ErrorActionPreference = 'SilentlyContinue'
$roots = [ordered]@{
  pictures = 'C:\Users\hpc01\Pictures\pro_new\RED_Ultimate_V1-main\RED_Ultimate'
  upload = 'C:\Users\hpc01\Pictures\pro_new\upload-clean\RED_Ultimate_V1-main\RED_Ultimate'
  documents = 'C:\Users\hpc01\Documents\pro_new\RED_Ultimate_V1-main\RED_Ultimate'
  androidstudio = 'C:\Users\hpc01\AndroidStudioProjects\pro\pro\RED_Ultimate_V1-main\RED_Ultimate'
  androidstudio_project = 'C:\Users\hpc01\AndroidStudioProjects\pro\project\pro\RED_Ultimate_V1-main\RED_Ultimate'
  pictures_pro = 'C:\Users\hpc01\Pictures\pro\RED_Ultimate_V1-main\RED_Ultimate'
  red = 'C:\red'
}
$files = @('docker-compose.yml','.env','README.md','todo.md','backend-server/build.gradle.kts','backend-server/src/main/resources/application.yml','admin_dashboard/package.json','admin_dashboard/src/App.tsx','red-app/build.gradle.kts','red-app/src/main/java/com/red/sovereign/calls/CallScreen.kt','red-app/src/main/java/com/red/sovereign/calls/IncomingPstnCallScreen.kt','red-app/src/main/java/com/red/sovereign/settings/DeviceSettingsScreen.kt','docs/DEVELOPMENT_PROGRESS_REPORT_AR.md')
$rows = foreach($name in $roots.Keys){ foreach($rel in $files){$p=Join-Path $roots[$name] $rel; if(Test-Path -LiteralPath $p){$i=Get-Item -LiteralPath $p; [pscustomobject]@{Source=$name;File=$rel;Exists=$true;Length=$i.Length;LastWriteTime=$i.LastWriteTime.ToString('o');SHA256=(Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash}} else {[pscustomobject]@{Source=$name;File=$rel;Exists=$false;Length=$null;LastWriteTime=$null;SHA256=$null}}}}
$out='C:\Users\hpc01\Pictures\RED_critical_comparison.csv'; $rows | Export-Csv $out -NoTypeInformation -Encoding UTF8; $rows | Format-Table Source,File,Exists,Length,LastWriteTime -AutoSize; Write-Output "REPORT=$out"
