# guard-dead-tree: red-app must never import dead-tree-only symbols from app/.
# Fails (exit 1) if settings mapping is broken or any forbidden import is found.
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
if (-not (Test-Path -LiteralPath (Join-Path $root "settings.gradle.kts"))) { $root = Get-Location }

$settings = Get-Content -LiteralPath (Join-Path $root "settings.gradle.kts") -Raw
if ($settings -notmatch 'project\(":app"\)\.projectDir\s*=\s*file\("red-app"\)') {
    Write-Output "GUARD-FAIL: settings.gradle.kts no longer maps :app -> red-app (dead tree would re-enter the build graph)"
    exit 1
}

$deadOnly = @(
  "CallForegroundService","CallOrchestrator","CallRepository","CallViewModel",
  "ChatDetailScreen","ChatListScreen","ChatViewModel","ConferenceScreens",
  "DinstarDashboardUI","DinstarLiveMonitor","GroupCreateScreen","GroupDetailScreen",
  "GroupIDManager","IdentityManager","MasterDao","MasterDeliveryEngine",
  "MasterFeatureSet","NetworkMonitor","NotificationBridge","OperatorIcons",
  "PermissionRequestScreen","PstnIncomingEventsSocket","PstnViewModel","QuantumGuard",
  "RedAudioManager","RedChatDetail","RedChatScreen","RedDeliveryEngine",
  "RedIdentityManager","RedMainHost","RedMasterDatabase","RedMasterModule",
  "RedMediaTransporter","RedMessageInput","RedPermissionManager","RedPushService",
  "RedRingtoneManager","RedSovereignApp","RedVoipMaster","SecurityVisualizerScreen",
  "SfuClient","SovereignAuthScreens","SyncEngine"
)
$pattern = "import\s+com\.red\.sovereign\.[\w.]*\b(" + ($deadOnly -join "|") + ")\b"
$hits = Get-ChildItem -LiteralPath (Join-Path $root "red-app\src") -Recurse -File -Filter "*.kt" |
    Select-String -Pattern $pattern
if ($hits) {
    Write-Output "GUARD-FAIL: red-app imports dead-tree-only symbols from app/:"
    $hits | ForEach-Object { Write-Output ("  " + $_.Path + ":" + $_.LineNumber + ": " + $_.Line.Trim()) }
    exit 1
}
Write-Output "GUARD-PASS: :app -> red-app mapping intact; no dead-tree imports in red-app."
