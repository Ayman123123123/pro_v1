# المرحلة 9 — عقود البيانات والمزامنة والأجهزة (2026-09-15)

> المصدر: تدقيق قراءة-فقط كامل + تنفيذ الإصلاحات على `arena/01a0a14b-pro-v1`.
> مسار الخادم: `RED_Ultimate_V1-main/RED_Ultimate/backend-server/src/main/kotlin/com/red/server/`

## 1) مَن يكتب أين (Who writes where)

| الكيان | الأساسي | الكاتب | ملاحظة العقد |
|---|---|---|---|
| الرسائل الخاصة/الجماعية/القنوات | Mongo (`messages`, `group_messages`, `channel_messages`) | `MessageService` + `GroupService` | التسلسل من `conversation_sequences` حصرًا (findAndModify ذري) |
| التسلسل الرقمي | Mongo `conversation_sequences` | `nextSequence*` فقط | **ممنوع أي مخصّص Redis** — حُذف `RedisSequenceGenerator` و`incrementSequence*` في P9 |
| الهوية/الحسابات/الأجهزة | Postgres (`user_accounts`, `user_devices`) | `DeviceController`, `RedApprovalService`, `DeviceEnrollmentService` | الجهاز: PENDING→APPROVED (ذاتي ≤5 أو أدمن) / REVOKED |
| جلسات Refresh | Postgres `refresh_sessions` | `RefreshTokenService` | تدوير + كشف إعادة الاستخدام (يُبطل عائلة الحساب) |
| عضوية القنوات وأدوارها | Postgres `channel_members` | `ChannelService` | OWNER/ADMIN/MODERATOR/SUBSCRIBER — **حُذف `ChannelMemberDocument` الميّت في P9** |
| عضوية المجموعات وأدوارها | Mongo `GroupMember` | `GroupService` | OWNER/ADMIN/MEMBER + `onlyAdminsCanSend` |
| بيانات القناة الوصفية | Mongo `ChannelDocument` | `ChannelService` | وصفية فقط — العضوية في Postgres |
| التثبيت | Postgres `pinned_messages` + مرآة `isPinned` في Mongo | `PinnedMessageService` | حدود: 5 خاص / 10 مجموعة / 20 قناة |
| التفاعلات | Mongo `reactions[]` + مرآة Postgres (best-effort) | `MessageService` | Mongo هي المرجع عند التعارض |
| الحضور الحي | Redis `red:presence:index` (ZSET redId←ms) + `red:online` (SET) | سوكت WS + `MessageService` (لمس عند الإرسال) | وصول مباشر عبر StringRedisTemplate (نمط معتمد) |
| الإيصالات المعلّقة | Mongo (`pendingFor`) + Redis pub/sub `red:messages:{redId}` | `MessageService`, `RedMasterHandler` | at-least-once — العميل يزيل التكرار بالـ UUID |
| إعدادات الاختفاء | Mongo أولًا ثم Postgres احتياطي | `MessageService.disappearingSecondsForConversation` | قراءة مزدوجة best-effort |
| الحظر/البلاغات/قوائم البث | Postgres | `ContactService`, `BroadcastController` | البث: الخادم يحفظ العضوية فقط، والـ fan-out من جهاز المالك (E2EE) |

## 2) فهارس Mongo (بعد P9)

| المجموعة | الفهرس | فرادة |
|---|---|---|
| `messages` | `uuid` | ✅ فريد |
| `messages` | `(conversationId, sequenceNumber)` | ✅ **فريد (P9)** — شبكة أمان تحت المخصّص الذري |
| `messages` | `(receiverId, status, sequenceNumber)` | عادي (Catch-up) |
| `messages` | `(conversationId, isPinned, pinnedAt↓)` / `(senderId, createdAt↓)` / `disappearAt` | عادي |
| `group_messages` | `(groupId, sequenceNumber)` | ✅ **فريد (P9)** |
| `channel_messages` | `(channelId, sequenceNumber)` | ✅ **فريد (P9)** |
| `pinned_messages`(Mongo) | `messageUuid` | ✅ فريد |

كل إنشاء فهرس في `indexes()` محمي بـ try/catch — تعارض خيارات فهرس قديم لا يُسقط الإقلاع.

## 3) مفاتيح Redis وTTLs (بعد P9)

| المفتاح | النوع | TTL | الكاتب |
|---|---|---|---|
| `red:presence:index` | ZSET | 40d + purge مجدول | WS connect/disconnect + لمس الإرسال |
| `red:online` | SET | بلا (دورة الحياة = السوكت) | `RedMasterHandler`, `UserStatusService` |
| `red:typing:{conv}:{user}` | String | 5s | `RedisManager.setTyping` (+ قناة `red:typing`) |
| `red:ratelimit:{scope}:{key}` | String | النافذة (Lua ذري) | `RedisManager.checkRateLimit` |
| `red:session:{tokenHash}` | String | = انتهاء الجلسة | API احتياطي (بلا منادٍ حاليًا) |
| `red:notify:unread:{userId}` | String | **30d منزلق (P9)** | `RedisManager` |
| `red:notify:queue:{userId}` | List ≤100 | **30d (P9)** | `RedisManager` |
| `red:call:signaling:{callId}` | String | 30m | `RedisManager` |
| `red:media:grant:{key}:{grantee}` | String | 1h | `RedisManager` |
| `red:search:recent:{userId}` | List ≤20 | **30d (P9)** | `RedisManager` |
| `red:metrics:realtime` | Hash | **48h منزلق (P9)** | `RedisManager` |

**محذوفات P9** (ميتة أو متناقضة): `red:seq/*`، `red:presence:{userId}`، `red:status:*`،
`red:otp/*` (OTP ممنوع أصلًا)، `red:device:cert/*`، `red:backup:progress/*` (توثيق فقط)،
`users:online` → أُعيدت تسميتها `red:online`.

## 4) عقد المزامنة

1. **التخصيص**: الخادم وحده يخصّص `sequenceNumber` (ذري عبر Mongo) — العميل لا يقترح أرقامًا فلا تعارض أصلًا.
2. **حلّ التعارض (P9)**: عند `DuplicateKeyException` — إن وُجد الـ UUID يُرجع المخزّن (idempotent) وإلا يُعاد التخصيص ويُحفظ مرة واحدة. مطبّق في: `processIncoming`، التحويل الخاص، التحويل للمجموعات.
3. **Catch-up**: `pendingFor(redId, deviceId)` عند اتصال WS + `getMissedMessages` (نطاق ≤500، سقف 50).
4. **إعادة التسليم**: مضخة مجدولة كل 30s (`redeliverPendingMessages`) — at-least-once.
5. **مزامنة أجهزة المرسل**: `sendToUser(exceptSessionId)` — الجلسات الأخرى للمرسل تستلم فورًا.
6. **ACK**: يفرض تطابق جهاز المستلم + تنزيل READ→DELIVERED حسب الخصوصية.

## 5) عقد الأجهزة والجلسات

- التسجيل: PENDING → اعتماد ذاتي (≤5 أجهزة) أو أدمن → شهادة جهاز.
- الحارس: `ApprovedDeviceSessionGuard` يقطع سوكت أي جهاز مُبطل كل إطار.
- الإلغاء: `DELETE /api/devices/{id}` يُبطل الجهاز + كل `refresh_sessions` الخاصة به.
- **(P9) إبطال الأخريات**: `POST /api/devices/revoke-others` — يُبطل كل الجلسات ما عدا جهاز الطلب (يُستخرج من `deviceId` في الـ Access Token)؛ جلسات الـ NULL-device تُبطل أيضًا؛ بلا deviceId تُبطل الكل (موثّق).
- المسح عن بُعد: `POST /security/wipe` → إشارة WS من نوع SYSTEM → `RemoteWipeAck` → `markRemoteWipeAcknowledged`.
- التطبيق: زر 🚪 في `DevicesScreen` + `DevicesApi.revokeOthers()`.

## 6) عقد الصلاحيات الدقيقة

- **المجموعات** (Mongo): OWNER/ADMIN/MEMBER — الطرد/الكتم/التثبيت للمالك والمشرف؛ `onlyAdminsCanSend` يُفرض على كل أنواع التشفير.
- **القنوات** (Postgres): بثّ أحادي — النشر العلوي للأدمن فقط (`canPost`)، الـ Thread مفتوح للأعضاء (`canReplyInThread`)، التثبيت OWNER/ADMIN.
- **قوائم البث** (Postgres): المالك فقط — CRUD + الأعضاء + `/send` تُرجع المستلمين للـ fan-out من جهازه.
- **إصلاح P9**: فحص تثبيت القنوات كان يقرأ مستند Mongo بلا كاتب (يفشل دائمًا) → أصبح يقرأ `channel_members` في Postgres.

## 7) تغييرات P9 المنفذة

| الملف | التغيير |
|---|---|
| `database/RedisManager.kt` | حذف 10 دوال ميتة/متناقضة + TTLs الستة + إصلاح `cleanUserData` + جدول عقد المفاتيح |
| `config/RedisSequenceGenerator.kt` | **حذف الملف** (مخطط مفاتيح خاطئ بلا منادٍ) |
| `websocket/RedMasterHandler.kt` + `social/UserStatusService.kt` | `users:online` → `red:online` |
| `messaging/MessageService.kt` | فهارس فرادة + حماية الإقلاع + حلّ تعارض Sequence في 3 مسارات حفظ |
| `messaging/PinnedMessageService.kt` | فحص عضوية القناة من Postgres (إصلاح عطل التثبيت) |
| `database/SovereignMongoDocuments.kt` | حذف `ChannelMemberDocument` |
| `social/ChannelService.kt` | إزالة استيراد ميّت |
| `auth/RefreshTokenService.kt` | `revokeOthers(userId, currentDeviceId): Int` |
| `auth/DeviceController.kt` | `POST /api/devices/revoke-others` |
| `red-app/.../auth/DevicesApi.kt` + `features/devices/DevicesScreen.kt` | `revokeOthers()` + زر 🚪 |
| `test/.../Phase9DataContractsTest.kt` | **7 اختبارات**: تعارض Sequence، idempotent، تثبيت قناة (نجاح/رفض)، revokeOthers، endpoint، TTLs |

## 8) ما لم يُغيَّر عمدًا (وقراراته)

- الوصول المباشر لـ `red:presence:index` في 8 ملفات — نمط معتمد وموحّد الدلالة، والتوحيد خلف Manager = churn بلا عائد.
- `GroupService.nextGroupSequence` المكرر لمنطق `nextSequence` — يعمل على نفس المجموعة والمفتاح (`group:{id}`)؛ توحيده تجميلي وخطر الاستيراد الدائري أعلى من نفعه.
- دوال Redis الاحتياطية (session/search/metrics/notify/call/media) بلا منادين — API بنية تحتية متماسك غير متناقض، أُبقيت مع TTLs بدل حذفها.
- فهرس `(conversationId, sequenceNumber)` غير الفريد القديم — إن وُجد في بيئة قائمة، إنشاء الفريد بجانبه قد يتعارض في الاسم؛ محمي بـ warn ولا يُسقط الإقلاع (تنظيفه يدوي عند الحاجة).
