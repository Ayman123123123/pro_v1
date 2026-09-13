#!/bin/sh
# guard-dead-tree: red-app must never import dead-tree-only symbols from app/.
# Fails (exit 1) if settings mapping is broken or any forbidden import is found.
set -eu
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SETTINGS="$ROOT/settings.gradle.kts"
if ! grep -q 'project(":app").projectDir = file("red-app")' "$SETTINGS"; then
  echo "GUARD-FAIL: settings.gradle.kts no longer maps :app -> red-app (dead tree would re-enter the build graph)"
  exit 1
fi
DEAD_ONLY="CallForegroundService|CallOrchestrator|CallRepository|CallViewModel|ChatDetailScreen|ChatListScreen|ChatViewModel|ConferenceScreens|DinstarDashboardUI|DinstarLiveMonitor|GroupCreateScreen|GroupDetailScreen|GroupIDManager|IdentityManager|MasterDao|MasterDeliveryEngine|MasterFeatureSet|NetworkMonitor|NotificationBridge|OperatorIcons|PermissionRequestScreen|PstnIncomingEventsSocket|PstnViewModel|QuantumGuard|RedAudioManager|RedChatDetail|RedChatScreen|RedDeliveryEngine|RedIdentityManager|RedMainHost|RedMasterDatabase|RedMasterModule|RedMediaTransporter|RedMessageInput|RedPermissionManager|RedPushService|RedRingtoneManager|RedSovereignApp|RedVoipMaster|SecurityVisualizerScreen|SfuClient|SovereignAuthScreens|SyncEngine"
if grep -rEn "import[[:space:]]+com\.red\.sovereign\.[A-Za-z0-9_.]*\b($DEAD_ONLY)\b" "$ROOT/red-app/src" ; then
  echo "GUARD-FAIL: red-app imports dead-tree-only symbols from app/ (see hits above)"
  exit 1
fi
echo "GUARD-PASS: :app -> red-app mapping intact; no dead-tree imports in red-app."
