# w2 — وكيل موثوقية الرسائل (RED Sovereign) — تشخيص + إصلاحات جراحية

- التاريخ: 2026-09-12 | النطاق: الرسائل فقط (خاصة + مجموعات) — بلا build/beta، بلا git.
- الملفات المعدَّلة: **2** (الحد 3): `red-app/.../core/RedConnectionService.kt` + `backend-server/.../websocket/RedMasterHandler.kt`.
- نسخ احتياطية: `.cluster/red-sovereign/backups/*.20260912-142009.bak` (نسخة قبل التعديل لكل ملف).
- الطريقة: قراءة أولاً، ثم استبدال جراحي بنيوي (byte-exact, ISO-8859-1 round-trip) — تحقّقنا: الملفان **UTF-8 صالح**، والأقواس متوازنة (215/215 و108/108)، والفرق مقابل النسخ الاحتياطية: svc ‎+45/−8‎ سطراً (كلها في مواضع الإصلاح)، master ‎+30/−0‎ (إضافة صرفة).
- ملاحظة تزامن: عمّال آخرون في `.cluster/red-sovereign` كانوا يعدّلون المشروع أثناء العمل (إضافة مسار `GET /api/messages/catchup` وشبكة resilient discovery). إصلاحاتي طُبّقت على الحالة الأحدث وتتقاطع مع مسار catchup الجديد دون تعطيله.

---

## 1) إرسال → سيرفر → مستلم: WS مباشر أم polling؟ وماذا لو كان المستلم أوفلاين؟

**التسليم حيّ عبر WebSocket مباشر (ثنائي، protobuf `RedProtos.RedRED`)** على المسار `/ws/master`:
- التسجيل: `WebSocketConfig.kt:67` (`registry.addHandler(redMasterHandler, "/ws/master")`).
- المعالج: `RedMasterHandler.kt:40` (`BinaryWebSocketHandler`)، والرسائل الواردة تُوزَّع في `handleEnvelopeSafely` (`RedMasterHandler.kt:84-100`).

**مسار الإرسال الكامل (خاص):**
1. العميل يرسل `ChatMessage` → `receiveMessage` (`RedMasterHandler.kt:135-149`).
2. التخزين في **MongoDB**: `MessageService.processIncoming` (`MessageService.kt:66-117`) يكتب `MessageDocument` في مجموعة `messages` (النموذج: `SovereignMongoDocuments.kt`، فهرس فريد على `uuid` عند `MessageService.kt:41`).
3. ترقيم تسلسلي لكل محادثة عبر `findAndModify` ذرّي: `nextSequence` (`MessageService.kt:556-562`).
4. ACK للمرسل بحالة `SENT`: `RedMasterHandler.kt:139` (`send(session, ack(stored, "SENT"))`).
5. بثّ للمستلم على جهازه المستهدف: `RedMasterHandler.kt:140` (`sendToDevice(stored.receiverId, stored.receiverDeviceId, ...)`).
6. مزامنة أجهزة المرسل الأخرى: `RedMasterHandler.kt:148` (`sendToUser(sender, ..., exceptSessionId)`).

**المستلم أوفلاين:** لا يوجد حقل last-seen cursor. الرسالة تُخزَّن بحالة `"SENT"` (`MessageService.kt:103`) وتُسلَّم عند عودة الاتصال بـ **replay قائم على الحالة (status=SENT)**:
- `RedMasterHandler.kt:245`: `messages.pendingFor(redId, protocolDeviceId).forEach { send(session, messageEnvelope(it)) }`.
- `MessageService.pendingFor` (`MessageService.kt:144-148`): يفلتر `receiverId + receiverDeviceId + status=SENT + deletedForEveryoneAt=null`، بسقف `limit.coerceIn(1, 50)`.
- إشعار FCM إن لم توجد جلسة حيّة: `RedMasterHandler.kt:143-146` → `NotificationService.sendChatMessagePush`.
- ACK المستلم يرفع الحالة: `acknowledge` (`MessageService.kt:162-176`) — شرط صارم: **نفس المستلم ونفس الجهاز** فقط من يملك ترقية `SENT→DELIVERED→READ`.

**ملاحظة (مسار ميت):** `MessageService.kt:115` ينشر `redis.convertAndSend("red:messages:${receiverId}", uuid)` لكن **لا مستهلك** له في الباكند — لا يعتمد عليه التسليم.

## 2) عند عودة الاتصال: هل يسحب التطبيق الفائت تلقائياً؟ وأين نقطة الفشل؟

نعم، عبر مسارين:

**(أ) دفع السيرفر للصفحة الأولى** عند فتح الجلسة: `RedMasterHandler.kt:245` بسقف 50 رسالة (`MessageService.kt:146`).
**(ب) سحب REST من العميل**: `RedConnectionService.catchUpMissedMessages` (`RedConnectionService.kt:436-474`) → `GET /api/messages/catchup?since=..&limit=..` (`MessageController.kt:34-55`) → `MessageService.pendingFor(redId, since, limit)` (`MessageService.kt:528-538`، سقف 500)، ويُستدعى على `CONNECTED` (`RedConnectionService.kt:396`).

**نقاط الفشل (مرصودة):**
1. **مؤشر `since` كان يُدفع إلى `now` دائماً** (`store.put("since", System.currentTimeMillis())` في النسخة السابقة) → أي تراكم أكبر من `limit` (200) يُفقد **نهائياً**، وكذلك أي رسالة تصل أثناء السحب. → **أُصلح (Fix B)**.
2. **السقف 50 عند الاتصال بلا إعادة محاولة دورية** → التراكم الأكبر من 50 يبقى عالقاً حتى إعادة الاتصال التالية (لا يوجد reconnect استباقي؛ heartbeat يبقي الجلسة). → **أُصلح (Fix C)**.
3. **لا يستخدم التطبيق `SYNC_REQ` إطلاقاً** (لا مطابقة واحدة لـ `SyncRequest` في `red-app`) رغم دعم السيرفر للـ backfill بمؤشر sequence: `RedMasterHandler.sync` (`RedMasterHandler.kt:215-218`) + `MessageService.getMissedMessages` (`MessageService.kt:150-160`). → مؤجَّل (خطة).
4. **الربط بمعرّف الجهاز**: `handleEnvelope` يرفض أي رسالة `receiverDeviceId != keyManager.protocolDeviceId()` (`RedConnectionService.kt:450`) **بلا ACK** → تبقى `SENT` وتُعاد في كل دورة. → مؤجَّل جزئياً (مغلق بسياسة الـ replay الجديدة على الأقل لا يُفقد).

## 3) المجموعات: هل التوزيع server-side fan-out؟ وهل يوجد ترتيب مضمون؟

**لا يوجد fan-out على السيرفر.** رسالة المجموعة تُخزَّن مرة واحدة كـ `MessageDocument` بمستلم واحد بعد التحقق من العضوية (`MessageService.kt:543-554`: المرسل والمستلم عضوان + `onlyAdminsCanSend`) — لا إنشاء `GroupMessageDocument` من مسار الإرسال (لا وجود لـ `GroupMessageDocument(` في مسار الإرسال؛ فقط في forward/delete-for-all: `MessageService.kt:370`، `GroupService.kt:478`).

**الـ fan-out يتم client-side:** `RedConnectionService.drainGroupSends` (`RedConnectionService.kt:236-266`) و`drainGroupPayloadSends` (`:273-299`): لكل عضو نسخة مستقلة `socket.sendEncrypted(recipient.redId, group.id, sendType, ...)`، مع توزيع مفاتيح Sender-Key منفصلاً (`:239-241` as `GROUP_KEY_DISTRIBUTION`).

**الترتيب:** لا ترتيب مضمون عبر المستلمين. كل نسخة fan-out تحصل على `sequenceNumber` مختلف من `nextSequence(conversationId)` (`MessageService.kt:556-562`)، والفرز الحقيقي عند العرض محلي في التطبيق بزمن الاستلام.

**الثغرة القاتلة (سبب الشكوى المباشر):** كل نسخ الـ fan-out كانت تُرسل **بنفس معرّف الرسالة** (`pending.clientId`) لأن كل دوال الإرسال العامة صارت تُمرّر clientId غير فارغ (`clientId ?: UuidV7.next()` في `RedConnectionService.kt:742-796`). والسيرفر يرفض أي تكرار لـ `uuid` موجّه لمستلم/جهاز مختلف:
```
MessageService.kt:79-85  →  require(existing.receiverId == message.receiverId && ... ) { "Message UUID collision" }
```
فالنتيجة: **تُخزَّن وتُسلَّم نسخة العضو الأول فقط، وتُهمَل بقية الأعضاء بصمت** (الاستثناء يُلتقط في `RedMasterHandler.kt:97-99` ويُسجَّل فقط، بلا ACK ولا تخزين). نفس الآلية تكسر الدردشة الخاصة متعددة الأجهزة (عدة `EncryptedEnvelope` لكل جهاز بنفس الـ id: `RedConnectionService.kt:322-325`). → **أُصلح (Fix A)**.

## 4) الظهور عند الطرفين: optimistic + تأكيد — أين تبقى معلّقة أو تختفي؟

- **Optimistic:** `persistOptimistic` (`RedConnectionService.kt:212-222`) يكتب صفاً في Room (`LocalHistoryEntity` بحالة افتراضية `SENT` — `Entities.kt`) وينشر `DecryptedMessageBus` للعرض الفوري.
- **التأكيد:** ACK من السيرفر (`RedMasterHandler.kt:139`) → `repository.updateMessageStatus` (`RedConnectionService.kt:542-544`) → `RedDao.updateMessageStatus` (`RedDao.kt:45-46`).
- **أين تعلق/تختفي:**
  1. **طوابير الإرسال in-memory فقط**: `pendingSends` / `pendingGroupSends` / `pendingGroupPayloadSends` (`RedConnectionService.kt:68-70`) + `RedWebSocketClient.pendingQueue` (`RedWebSocketClient.kt:29`). تموت مع العملية، ولا يوجد أي مسار يعيد إرسال الصفوف المعلّقة بعد إعادة التشغيل → **الرسالة تظهر عند المرسل فقط وتبقى "SENT" للأبد ولا تصل الطرف الآخر**. (بنية Outbox دائمة موجودة لكنها **ميتة**: `OutboxRetryWorker` يقرأ من `OutboxDao` فقط، ولا شيء يستدعي `OutboxRepository.enqueue` — لا مطابقة واحدة لـ `OutboxMessageEntity(` خارج OutboxRepository نفسه.)
  2. ACK للأعضاء 2..N في المجموعة يحمل معرّفات مختلفة عن الصف المحلي (id = clientId) → تحديث حالة للعضو الأول فقط (سلوك مقبول، غير معروض كخطأ).
  3. عدم تطابق `receiverDeviceId` (بند 2/4) → لا تسليم ولا ACK.
  4. **فشل فك التشفير لا يرسل ACK** (`RedConnectionService.kt:472-475` → لا `acknowledge`) → إعادة تسليم متكررة بلا ظهور.

## 5) أعلى 3 ثغرات مرتّبة + ما أُصلح + ما أُجّل

| # | الخطورة | الثغرة | القرار |
|---|---|---|---|
| 1 | **حرِجة** | عميل: إعادة استخدام نفس UUID لكل أهداف fan-out → رفض السيرفر لما بعد الأول → **المجموعة تصل لعضو واحد**؛ والخاص متعدد الأجهزة لجهاز واحد | **أُصلحت (Fix A)** |
| 2 | عالية | عميل: مؤشر catchup ينتقل إلى `now` → فقدان نهائي لكل ما زاد عن حد الصفحة | **أُصلحت (Fix B)** |
| 3 | عالية | سيرفر: replay عند الاتصال بسقف 50 وبلا إعادة محاولة دورية → رسائل عالقة لساعات | **أُصلحت (Fix C)** |
| 4 | عالية | لا إعادة إرسال بعد إعادة التشغيل (طوابير in-memory، Outbox دائم غير موصول) | مؤجَّلة (خطة) |
| 5 | متوسطة | لا `SYNC_REQ`/مؤشر sequence من التطبيق (backfill لكل محادثة) | مؤجَّلة (خطة) |
| 6 | متوسطة | صدى رسالة المجموعة للمرسل: N نسخة في جدول `messages` (غير المعروض في الواجهة) | مؤجَّلة (خطة) |
| 7 | منخفضة | `red:messages:{id}` pub/sub بلا مستهلك (كود ميت) | مؤجَّلة |

---

## ما أصلحته (قبل/بعد)

### Fix A — عميل: معرّف فريد لكل هدف fan-out (حرِج)
الملف: `red-app/src/main/java/com/red/sovereign/core/RedConnectionService.kt`

**قبل** (كان في 3 مواضع — سطران في drainGroupSends/drainGroupPayloadSends وسطر في sendEncryptedPayload):
```kotlin
val id = socket.sendEncrypted(recipient.redId, group.id, sendType, keyManager.protocolDeviceId(), envelope, pending.clientId)
```
**بعد** (النسخة الحالية: نفس الأسطر 258، 295، 323):
```kotlin
var fanoutClientIdUsed = false                    // يُعلن قبل حلقة forEach (أسطر 254، 291، 320)
...
val id = socket.sendEncrypted(recipient.redId, group.id, sendType, keyManager.protocolDeviceId(), envelope, if (fanoutClientIdUsed) null else pending.clientId.also { fanoutClientIdUsed = true })
```
مع تعليق `AUTO-FIX (message reliability)` موضِّح للسبب. النتيجة: النسخة الأولى تحتفظ بـ clientId (مطابقة للصف المحلي optimistic)، والبقية تحصل على UUIDv7 جديدة → لا تصادم على السيرفر.
- الموضع الثالث (الخاص متعدد الأجهزة): `RedConnectionService.kt:322-325`.

### Fix B — عميل: مؤشر catchup غير مُفقِد للرسائل
الملف نفسه، الدالة `catchUpMissedMessages` (الآن الأسطر 436-474).

**قبل:** `val since = store.get(...) ?: ...` ثم طلب واحد `limit=200` ثم `store.put("since", System.currentTimeMillis().toString())`.
**بعد:** `var cursor` + حلقة صفحات (`CATCHUP_MAX_PAGES`) تُقدّم المؤشر إلى **أحدث رسالة عادت فعلاً** (`newest`)، مع توقف آمن عند انعدام التقدم:
```kotlin
while (pages < CATCHUP_MAX_PAGES) { ... if (newest <= cursor) break; cursor = newest; if (arr.length() < CATCHUP_PAGE_SIZE) break }
store.put("since", cursor.toString())
```
+ ثوابت: `RedConnectionService.kt:758-760` (`CATCHUP_PAGE_SIZE = 200` سطر 759، `CATCHUP_MAX_PAGES = 25` سطر 760).

### Fix C — سيرفر: مضخة إعادة تسليم at-least-once
الملف: `backend-server/src/main/kotlin/com/red/server/websocket/RedMasterHandler.kt`

**قبل:** لا شيء (التسليم العالق ينتظر إعادة اتصال فقط).
**بعد:** دالة جديدة `redeliverPendingMessages()` — `RedMasterHandler.kt:281-302`:
```kotlin
@Scheduled(fixedDelay = 30_000)
fun redeliverPendingMessages() {
    sessions.forEach { (redId, perUser) ->
        perUser.values.filter { it.isOpen }.forEach deviceLoop@{ session ->
            val deviceId = session.attributes["protocolDeviceId"] as? Int ?: return@deviceLoop
            runCatching {
                val cutoff = Instant.now().minusSeconds(PENDING_REDELIVER_MIN_AGE_SECONDS)
                messages.pendingFor(redId, deviceId, PENDING_REDELIVER_LIMIT)
                    .filter { it.createdAt.isBefore(cutoff) }
                    .forEach { send(session, messageEnvelope(it)) }
            }.onFailure { log.debug("redeliverPendingMessages failed for {}: {}", redId, it.message) }
        }
    }
}
```
+ ثوابت في `private companion object` — `RedMasterHandler.kt:381-386` (`PENDING_REDELIVER_LIMIT = 50`, `PENDING_REDELIVER_MIN_AGE_SECONDS = 10L`).
آمن من التكرار: العميل يخزّن بالمعرّف (`insertLocalHistory` = REPLACE) فإطار مكرّر = لا أثر. ويعتمد نفس نمط `@Scheduled` الموجود مسبقاً في الملف (`cleanupStalePresence`, سطر 269).

---

## ما أجّلته ولماذا (خطة)

1. **إعادة الإرسال بعد إعادة التشغيل (الأهم).** السبب: يحتاج وصل الطوابير in-memory ببنية Outbox الدائمة (تُنشئ صف Outbox في `ACTION_SEND_*` + عامل إعادة) أو مسح Room للصفوف المعلّقة بحالة `SENT` وإعادة حقنها. هذا يمسّ 2-3 ملفات ويحتاج قراراً حول «إعادة إرسال أي صف SENT» (قد يعيد إرسال رسائل سُلّمت فعلاً وفُقد ACKها — وهو مقبول مع idempotency، لكنه يحتاج اختبار). الخطة: (أ) في `onStartCommand` لمسارات الإرسال: `OutboxRepository.enqueue(...)` بجانب الطوابير in-memory؛ (ب) على `CONNECTED`: استدعاء `OutboxRetryWorker` / مسح `local_history WHERE outgoing=1 AND status='SENT'` وإعادة الحقن بنفس clientId=id.
2. **`SYNC_REQ` من التطبيق.** السبب: يحتاج تتبّع آخر sequence لكل محادثة محلياً (عمود جديد أو جدول) ثم طلب `SyncRequest{conversationId, fromSequence}` — تغيير مخطط Room + منطق واجهة. الفائدة: backfill دقيق ومحدود الحجم بدل دفع كل SENT.
3. **إزالة تكرار صدى المجموعة للمرسل (N نسخ في جدول `messages`).** السبب: غير مرئي حالياً (الواجهة تقرأ `local_history` لا `messages`)، والإصلاح الأنظف على السيرفر (عدم صدى النسخ غير القياسية إلى أجهزة المرسل) يتطلب وسم نسخ الـ fan-out. لا خطر حالياً، لذا أُجّل.
4. **ملف `messages` غير المقروء / `MessageEntity`.** لاحظت أن `saveIncomingMessage` يكتب جدولاً لا تقرأه الواجهة؛ توحيد المخزنين أكبر من نطاق جراحي.
5. **سياسة ACK عند فشل فك التشفير** (سطر 472-475): إبقاء السلوك الحالي (retry) مقصود لعدم فقدان الرسائل؛ التحسين المقترح لاحقاً: عدّاد محاولات مع DEAD_LETTER بعد N.

## قيود التزمتُ بها
- بلا build/gradle، بلا git، بلا تعديل خارج نطاق الرسائل، UTF-8 محفوظ (تحقّق byte-level + UTF-8 validity + توازن الأقواس).
- ملفان معدَّلان فقط (الحد 3)، ونسخ احتياطية مؤرَّخة في `.cluster/red-sovereign/backups/`.
