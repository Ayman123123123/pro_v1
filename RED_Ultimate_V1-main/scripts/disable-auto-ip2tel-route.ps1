[CmdletBinding()]
param([string]$GatewayHost="192.168.11.1",[string]$Username="admin",[string]$Password,[string]$EnvFile)
$ErrorActionPreference="Stop"
$scriptDir=Split-Path -Parent $PSCommandPath
if(-not $Password){
  if(-not $EnvFile){ $EnvFile=Join-Path $scriptDir "..\RED_Ultimate_V1-main\RED_Ultimate\.env" }
  $line=Select-String -LiteralPath $EnvFile -Pattern "^DINSTAR_PASSWORD="|Select-Object -First 1
  if(-not $line){ throw "DINSTAR_PASSWORD not found" }
  $Password=$line.Line.Substring("DINSTAR_PASSWORD=".Length)
}
[System.Net.ServicePointManager]::ServerCertificateValidationCallback = { $true }
[System.Net.ServicePointManager]::SecurityProtocol = "Tls12,Tls11,Tls"
$jar=Join-Path $env:TEMP "dar-$([guid]::NewGuid()).txt"
try {
  $lb="username=$([uri]::EscapeDataString($Username))&password=$([uri]::EscapeDataString($Password))"
  Invoke-WebRequest -Uri "https://$GatewayHost/goform/IADIdentityAuth" -Method POST -Body $lb -ContentType "application/x-www-form-urlencoded" -SessionVariable sess -TimeoutSec 20 -UseBasicParsing | Out-Null
  $fr=Invoke-WebRequest -Uri "https://$GatewayHost/enSIPCfg.htm" -WebSession $sess -TimeoutSec 15 -UseBasicParsing
  $html=$fr.Content
  $fields=@{}; $radioGroups=@{}
  foreach($m in [regex]::Matches($html,'<input[^>]*type="radio"[^>]*name="([^"]+)"[^>]*value="([^"]+)"[^>]*(checked)?')){
    $n=$m.Groups[1].Value; $v=$m.Groups[2].Value
    if($m.Groups[3].Value){ $radioGroups[$n]=$v } elseif(-not $radioGroups.ContainsKey($n)){ $radioGroups[$n]=$v }
  }
  foreach($m in [regex]::Matches($html,'<select[^>]*name="([^"]+)"[^>]*>(.*?)</select>',"Singleline")){
    $n=$m.Groups[1].Value; $body=$m.Groups[2].Value
    $o=[regex]::Match($body,'<option[^>]*value="([^"]+)"[^>]*selected')
    if(-not $o.Success){ $o=[regex]::Match($body,'<option[^>]*selected[^>]*value="([^"]+)"') }
    if($o.Success){ $fields[$n]=$o.Groups[1].Value }
  }
  foreach($m in [regex]::Matches($html,'<input[^>]*name="([^"]+)"[^>]*(?:value="([^"]*)")?')){
    $n=$m.Groups[1].Value; $v=$m.Groups[2].Value
    if(-not $fields.ContainsKey($n) -and -not $radioGroups.ContainsKey($n)){ $fields[$n]=$v }
  }
  Write-Host "[read] fields=$($fields.Count) radios=$($radioGroups.Count)"
  Write-Host "[current] AutoIp2TelRoute=$($radioGroups['AutoIp2TelRoute']) RoutePrefixType=$($fields['RoutePrefixType'])"
  $parts=@()
  foreach($k in $fields.Keys){ $parts+="$( [uri]::EscapeDataString($k) )=$( [uri]::EscapeDataString($fields[$k]) )" }
  foreach($k in $radioGroups.Keys){ $parts+="$( [uri]::EscapeDataString($k) )=$( [uri]::EscapeDataString($radioGroups[$k]) )" }
  $parts=$parts|Where-Object{ $_ -notmatch "^AutoIp2TelRoute=" }; $parts+="AutoIp2TelRoute=0"
  $parts=$parts|Where-Object{ $_ -notmatch "^RoutePrefixType=" }; $parts+="RoutePrefixType=0"
  $parts=$parts|Where-Object{ $_ -notmatch "^RouteCalledPrefixType=" }; $parts+="RouteCalledPrefixType=0"
  $pb=$parts-join"&"
  Write-Host "[post] $($parts.Count) fields -> /goform/SipCfg"
  $pr=Invoke-WebRequest -Uri "https://$GatewayHost/goform/SipCfg" -Method POST -Body $pb -ContentType "application/x-www-form-urlencoded" -WebSession $sess -TimeoutSec 20 -UseBasicParsing
  Write-Host "[post] HTTP $($pr.StatusCode)"
  Start-Sleep -Seconds 3
  Write-Host "[watch] monitoring 60s..."
  for($i=1;$i -le 3;$i++){
    Start-Sleep -Seconds 20
    $rr=Invoke-WebRequest -Uri "https://$GatewayHost/GetRoutingList?routing_type=ip-to-tel" -WebSession $sess -TimeoutSec 15 -UseBasicParsing
    $jr=($rr.Content|ConvertFrom-Json).routing
    $idxs=($jr.index -join ",")
    $miss=@(0,1,2,3,4,5,6,7)|Where-Object{ $_ -notin $jr.index }
    if($miss.Count -eq 0){ Write-Host "  t+$($i*20)s: [$idxs] STABLE" -ForegroundColor Green }
    else{ Write-Host "  t+$($i*20)s: [$idxs] MISSING:$($miss -join ',')" -ForegroundColor Yellow }
  }
} catch { Write-Host "ERROR: $($_.Exception.Message)" -ForegroundColor Red }
finally { if(Test-Path $jar){ Remove-Item $jar -Force } }