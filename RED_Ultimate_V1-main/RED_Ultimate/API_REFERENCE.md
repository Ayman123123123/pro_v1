# 🏛️ YOUNES Sovereign API — المرجع الكامل

_مولّد تلقائيًا من المتحكمات (Spring Boot) — كل الـ endpoints المتاحة_

## المصادقة
- `POST /api/auth/login` → يمنح `accessToken` + `refreshToken`
- أرسل `Authorization: Bearer <accessToken>` لكل الطلبات المحمية
- `POST /api/auth/refresh` مع `{refreshToken}` لتجديد التوكن

## قواعد الحماية
- `/api/admin/**`, `/api/master/admin/**`, `/api/master/v1/**`, `/api/live/admin/**` → **ADMIN فقط**
- `/ws/**` → WebSocket مع JWT handshake
- `/health`, `/actuator/health` → عام

## المسارات

| الطريقة | المسار | الملف |
|---|---|---|
| DELETE | `/api/contacts/{redId}` | `ContactController.kt` |
| DELETE | `/api/contacts/{redId}/block` | `ContactController.kt` |
| DELETE | `/api/devices/{deviceId}` | `DeviceController.kt` |
| DELETE | `/api/feed/following/{redId}` | `FeedController.kt` |
| DELETE | `/api/feed/posts/{postId}` | `FeedController.kt` |
| DELETE | `/api/groups/{id}` | `GroupController.kt` |
| DELETE | `/api/groups/{id}/invites/{inviteId}` | `GroupController.kt` |
| DELETE | `/api/groups/{id}/members/{userId}` | `GroupController.kt` |
| DELETE | `/api/groups/{id}/membership` | `GroupController.kt` |
| DELETE | `/api/media/users/{userId}/{fileName:.+}` | `MediaController.kt` |
| DELETE | `/api/notifications/{id}` | `NotificationController.kt` |
| DELETE | `/api/stories/{id}` | `StoryController.kt` |
| GET | `/api/admin/audit` | `AuditController.kt` |
| GET | `/api/admin/dinstar/capabilities` | `DinstarController.kt` |
| GET | `/api/admin/dinstar/cdr` | `DinstarController.kt` |
| GET | `/api/admin/dinstar/device-status` | `DinstarController.kt` |
| GET | `/api/admin/dinstar/discover` | `DinstarController.kt` |
| GET | `/api/admin/dinstar/inventory` | `DinstarController.kt` |
| GET | `/api/admin/dinstar/ports/{port}` | `DinstarController.kt` |
| GET | `/api/admin/dinstar/ports/{port}/ussd` | `DinstarController.kt` |
| GET | `/api/admin/dinstar/sms/incoming` | `DinstarSmsController.kt` |
| GET | `/api/admin/dinstar/sms/queue` | `DinstarSmsController.kt` |
| GET | `/api/admin/dinstar/status` | `DinstarController.kt` |
| GET | `/api/admin/moderation/reports` | `ModerationController.kt` |
| GET | `/api/admin/monitor/stats` | `AdminMonitorController.kt` |
| GET | `/api/admin/stories/monitor` | `AdminController.kt` |
| GET | `/api/admin/users` | `AdminController.kt` |
| GET | `/api/admin/users/pending` | `AdminController.kt` |
| GET | `/api/calls/history` | `CallHistoryController.kt` |
| GET | `/api/calls/ice-servers` | `IceServerController.kt` |
| GET | `/api/contacts` | `ContactController.kt` |
| GET | `/api/contacts/blocked` | `ContactController.kt` |
| GET | `/api/contacts/presence` | `ContactController.kt` |
| GET | `/api/contacts/requests` | `ContactController.kt` |
| GET | `/api/devices` | `DeviceController.kt` |
| GET | `/api/devices/{deviceId}/prekeys/stock` | `OneTimePreKeyController.kt` |
| GET | `/api/directory/search` | `PublicDirectoryController.kt` |
| GET | `/api/feed` | `FeedController.kt` |
| GET | `/api/feed/following` | `FeedController.kt` |
| GET | `/api/feed/posts/{postId}/thread` | `FeedController.kt` |
| GET | `/api/groups` | `GroupController.kt` |
| GET | `/api/groups/{id}` | `GroupController.kt` |
| GET | `/api/groups/{id}/join-requests` | `GroupController.kt` |
| GET | `/api/identity/authority` | `IdentityAuthorityController.kt` |
| GET | `/api/identity/directory/{redId}` | `IdentityDirectoryController.kt` |
| GET | `/api/identity/directory/{redId}/{deviceId}/prekey` | `IdentityDirectoryController.kt` |
| GET | `/api/live/streams` | `LiveStreamController.kt` |
| GET | `/api/live/streams/{streamId}/viewers` | `LiveStreamController.kt` |
| GET | `/api/master/admin/hardware/dinstar/slots` | `AdminMasterController.kt` |
| GET | `/api/master/admin/system/stats` | `AdminMasterController.kt` |
| GET | `/api/master/admin/users/pending` | `AdminMasterController.kt` |
| GET | `/api/master/v1/auth/pending` | `RedMasterController.kt` |
| GET | `/api/master/v1/hardware/dinstar/slots` | `RedMasterController.kt` |
| GET | `/api/master/v1/media/active-calls` | `RedMasterController.kt` |
| GET | `/api/master/v1/stats/realtime` | `RedMasterController.kt` |
| GET | `/api/media/users/{userId}/{fileName:.+}` | `MediaController.kt` |
| GET | `/api/notifications` | `NotificationController.kt` |
| GET | `/api/notifications/preferences` | `NotificationController.kt` |
| GET | `/api/notifications/unread-count` | `NotificationController.kt` |
| GET | `/api/pstn/status` | `PstnCallController.kt` |
| GET | `/api/sfu/groups/{groupId}/ticket` | `SfuTicketController.kt` |
| GET | `/api/social/online-contacts` | `StatusController.kt` |
| GET | `/api/social/privacy` | `StatusController.kt` |
| GET | `/api/social/status/{userId}` | `StatusController.kt` |
| GET | `/api/stories` | `StoryController.kt` |
| GET | `/health` | `HealthController.kt` |
| PATCH | `/api/admin/moderation/reports/{reportId}` | `ModerationController.kt` |
| PATCH | `/api/groups/{id}/avatar` | `GroupController.kt` |
| PATCH | `/api/groups/{id}/members/{userId}` | `GroupController.kt` |
| POST | `/api/admin/dinstar/config/sip` | `DinstarController.kt` |
| POST | `/api/admin/dinstar/dial` | `DinstarController.kt` |
| POST | `/api/admin/dinstar/ports/{port}/callforward` | `DinstarController.kt` |
| POST | `/api/admin/dinstar/ports/{port}/power` | `DinstarController.kt` |
| POST | `/api/admin/dinstar/ports/{port}/reset` | `DinstarController.kt` |
| POST | `/api/admin/dinstar/ports/{port}/ussd` | `DinstarController.kt` |
| POST | `/api/admin/dinstar/reboot` | `DinstarController.kt` |
| POST | `/api/admin/dinstar/sms/deliver` | `DinstarSmsController.kt` |
| POST | `/api/admin/dinstar/sms/result` | `DinstarSmsController.kt` |
| POST | `/api/admin/dinstar/sms/send` | `DinstarSmsController.kt` |
| POST | `/api/admin/dinstar/sms/stop` | `DinstarSmsController.kt` |
| POST | `/api/admin/security/kill-switch` | `AdminController.kt` |
| POST | `/api/admin/security/wipe` | `AdminController.kt` |
| POST | `/api/admin/users/action` | `AdminController.kt` |
| POST | `/api/admin/users/update-status` | `AdminController.kt` |
| POST | `/api/admin/ws-ticket` | `AdminWebSocketTicketController.kt` |
| POST | `/api/auth/login` | `AuthController.kt` |
| POST | `/api/auth/logout` | `AuthController.kt` |
| POST | `/api/auth/recover` | `AuthController.kt` |
| POST | `/api/auth/refresh` | `AuthController.kt` |
| POST | `/api/auth/register` | `AuthController.kt` |
| POST | `/api/contacts/reports` | `ContactController.kt` |
| POST | `/api/contacts/requests/{redId}` | `ContactController.kt` |
| POST | `/api/contacts/requests/{requestId}/accept` | `ContactController.kt` |
| POST | `/api/contacts/requests/{requestId}/reject` | `ContactController.kt` |
| POST | `/api/contacts/{redId}/block` | `ContactController.kt` |
| POST | `/api/devices/{deviceId}/prekeys` | `OneTimePreKeyController.kt` |
| POST | `/api/feed/following/{redId}` | `FeedController.kt` |
| POST | `/api/feed/posts` | `FeedController.kt` |
| POST | `/api/feed/posts/{postId}/reactions` | `FeedController.kt` |
| POST | `/api/feed/posts/{postId}/vote` | `FeedController.kt` |
| POST | `/api/groups` | `GroupController.kt` |
| POST | `/api/groups/join-requests` | `GroupController.kt` |
| POST | `/api/groups/{id}/invites` | `GroupController.kt` |
| POST | `/api/groups/{id}/join-requests/{requestId}` | `GroupController.kt` |
| POST | `/api/groups/{id}/members` | `GroupController.kt` |
| POST | `/api/groups/{id}/transfer-ownership` | `GroupController.kt` |
| POST | `/api/live/admin/streams/{streamId}/start` | `LiveStreamController.kt` |
| POST | `/api/live/admin/streams/{streamId}/stop` | `LiveStreamController.kt` |
| POST | `/api/live/streams/{streamId}/viewers/join` | `LiveStreamController.kt` |
| POST | `/api/live/streams/{streamId}/viewers/leave` | `LiveStreamController.kt` |
| POST | `/api/master/admin/hardware/dinstar/action` | `AdminMasterController.kt` |
| POST | `/api/master/admin/users/approve` | `AdminMasterController.kt` |
| POST | `/api/master/v1/auth/action` | `RedMasterController.kt` |
| POST | `/api/master/v1/security/wipe` | `RedMasterController.kt` |
| POST | `/api/media/consumes = [MediaType.MULTIPART_FORM_DATA_VALUE]` | `MediaController.kt` |
| POST | `/api/media/grants` | `MediaController.kt` |
| POST | `/api/pstn/calls` | `PstnCallController.kt` |
| POST | `/api/pstn/calls/{callId}/hangup` | `PstnCallController.kt` |
| POST | `/api/stories` | `StoryController.kt` |
| POST | `/api/stories/{id}/view` | `StoryController.kt` |
| PUT | `/api/admin/dinstar/inventory/{gatewayId}/ports/{portIndex}` | `DinstarController.kt` |
| PUT | `/api/admin/users/pstn` | `PstnAuthorizationController.kt` |
| PUT | `/api/notifications/preferences` | `NotificationController.kt` |
| PUT | `/api/notifications/read-all` | `NotificationController.kt` |
| PUT | `/api/notifications/{id}/read` | `NotificationController.kt` |
| PUT | `/api/social/privacy` | `StatusController.kt` |
| PUT | `/api/social/status` | `StatusController.kt` |

## WebSocket
| المسار | الوصف |
|---|---|
| `/ws/master` | رسائل مشفرة + إشعارات + حالة اتصال |
| `/ws/calls` | إشارات WebRTC للمكالمات |
| `/ws/typing` | حالة يكتب الآن |
| `/ws/admin/logs` | سجلات حية للمسؤول (بتذكرة) |
