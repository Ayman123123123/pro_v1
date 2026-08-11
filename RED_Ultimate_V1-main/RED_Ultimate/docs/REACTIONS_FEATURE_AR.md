# 🎭 تفاعلات الإيموجي على الرسائل (Message Reactions) — E2EE

> **التاريخ:** 2026-08-11
> **الميزة:** A2 من خارطة التطوير — Reactions على الرسائل (فردية + جماعية)
> **الحالة:** مُنفّذة عبر كل الطبقات (Proto → DB → Backend → UI) مع اختبارات وحدة

---

## 1) الهدف

تمكين المستخدم من التفاعل على أي رسالة (فردية أو جماعية) بإيموجي سريع (👍 ❤️ 😂 …)، مع عرض التفاعلات كـ chips تحت الرسالة مع عدّاد لكل إيموجي. ميزة تنافسية يمتلكها كل المنافسين (Signal/WhatsApp/Telegram) وكانت **مفقودة تماماً** من الكود (لا في proto، لا في DB، لا في UI).

## 2) التصميم: E2EE ضمن حمولة RICH_TEXT

**القرار المعماري الأهم:** التفاعل يُرسل كـ `RICH_TEXT` مشفّرة عبر نفس مسار الرسائل العادية.

- **المحادثة الفردية:** `signal.encrypt()` → ciphertext → الخادم يوجّهه فقط.
- **المجموعة:** `groupCrypto.prepare()` (Sender Keys) → ciphertext لكل عضو.
- **الخادم لا يرى الإيموجي** — يرى ciphertext فقط. ✅ متوافق تماماً مع مبدأ السيادة.
- **لا تغيير في الـ backend** — `RICH_TEXT` مقبول بالفعل في `MessageService.TYPES`.

هذا التصميم يعيد استخدام بنية `RichMessage` الموجودة (التي تدعم `EDIT`/`DELETE`/`STORY_REPLY`) بدلاً من إنشاء proto message جديد، مما يقلل سطح الهجوم ويحافظ على البنية.

## 3) عقد البروتوكول (RichMessage)

أُضيف حقلان + action جديدان إلى `RichMessage` (`core/RichMessage.kt`):

```kotlin
val reactionOf: String? = null,  // id الرسالة المُتفاعل معها
val emoji: String? = null        // الإيموجي (للإضافة)؛ null مع REACTION_REMOVE
```

| action | الشروط | المعنى |
|---|---|---|
| `REACTION` | `reactionOf != null && emoji != null` | إضافة/استبدال تفاعل |
| `REACTION_REMOVE` | `reactionOf != null` | إزالة تفاعل المُرسِل عن رسالة |

**Toggle:** لكل مستخدم تفاعل واحد لكل رسالة. إعادة إرسال نفس الإيموجي = إزالة (يُرسل `REACTION_REMOVE`). إرسال إيموجي مختلف = استبدال (upsert بالمفتاح المركّب `messageId + senderId`).

## 4) التخزين المحلي (Room)

أُضيف `MessageReactionEntity` في `core/database/Entities.kt`:

```kotlin
@Entity(tableName = "message_reactions",
        primaryKeys = ["messageId", "senderId"],  // تفاعل واحد لكل (رسالة، مُرسِل)
        indices = [Index("conversationId"), Index("messageId")])
data class MessageReactionEntity(
    val messageId: String, val conversationId: String,
    val senderId: String, val emoji: String, val timestamp: Long
)
```

- **Migration آمن:** `REACTION_MIGRATION_1_2` يضيف الجدول دون فقدان البيانات المشفّرة الموجودة (DB version 1→2).
- **SQLCipher:** الجدول داخل نفس قاعدة البيانات المشفّرة (`red_sovereign.db`).
- DAO: `upsertReaction`, `deleteReaction`, `reactionsForConversation` (Flow), `reactionsForMessage`.
- حذف المحادثة يصحب بحذف تفاعلاتها (`deleteReactionsByConversation`).

## 5) التدفق (Transport)

`core/RedConnectionService.kt`:

- **الإرسال:** `sendReaction()` / `removeReaction()` / `sendGroupReaction()` / `removeGroupReaction()` — كلها تعيد استخدام `sendRichText`/`sendGroupRichText`.
- **الاستقبال (`onEnvelope`):** عند ورود `RICH_TEXT` بـ `action=REACTION`، يُطبّق على جدول `message_reactions` (لا يُحفظ كرسالة) ويُبَث عبر `ReactionEventBus`.
- **التطبيق المحلي للصادر:** `applyOutgoingReactionLocally()` يطبّق تفاعل المُرسِل محلياً فوراً دون انتظار round-trip، ويُبَثه للواجهة.
- **منع التلوث:** رسائل `REACTION`/`REACTION_REMOVE` **لا تُحفظ في `local_history`** ولا تُنشر عبر `DecryptedMessageBus` (ليست رسائل). `resolveRichMessages()` يتجاهلها أيضاً كإجراء أمان.

## 6) واجهة المستخدم (RTL)

`ui/RedDashboard.kt`:

- **`MessageReactions` composable:** chips تفاعلية تحت كل رسالة، مجمّعة حسب الإيموجي مع عدّاد. تفاعل المستخدم نفسه مظلل بلون `YounesEmerald` + border. الضغط = toggle.
- **`ReactionEmojiBar` composable:** شريط 10 إيموجي سريعة أعلى قائمة إجراءات الرسالة (الوصول عبر long-press على الرسالة).
- **تحديث فوري:** `ReactionEventBus` يحدّث الـ chips فوراً عند ورود تفاعل (وارد أو صادر) دون إعادة تحميل المحادثة.
- **تحميل من DB:** `reactionsForConversation(convId)` Flow يحمّل التفاعلات المخزّنة عند فتح المحادثة.
- يعمل في **المحادثة الفردية والمجموعة** معاً.
- **RTL:** كل النصوص والـ chips عربية ومن اليمين لليسار، متسقة مع هوية يونس.

## 7) نموذج التهديد (Threat Model) — مختصر

| التهديد | التخفيف |
|---|---|
| الخادم يقرأ الإيموجي | ✅ مستحيل — الإيموجي داخل ciphertext E2EE (Signal/Sender Keys) |
| تفاعل مزوّر من حساب ثالث | ✅ الـ `senderId` يُؤخذ من envelope المُوقّع/المُوثّق، لا من الحمولة |
| تكرار التفاعل (replay) | ✅ toggle بالمفتاح المركّب `messageId + senderId` (upsert) |
| تسريب عبر الإشعارات | ✅ التفاعلات لا تطلق إشعارات (ليست رسائل) |
| فقدان البيانات عند ترقية DB | ✅ Migration 1→2 يضيف جدولاً جديداً فقط |

## 8) الاختبارات

`red-app/src/test/java/com/red/sovereign/core/RichMessageTest.kt` — 11 اختبار وحدة جديد:

- ✅ round-trip encode/decode لـ `REACTION` و `REACTION_REMOVE`
- ✅ `REACTION` يتطلب `reactionOf` + `emoji`
- ✅ `REACTION_REMOVE` يتطلب `reactionOf` فقط
- ✅ رفض emoji فارغ/طويل جداً
- ✅ الرسائل العادية والتعديل لا تتأثر بتحقق التفاعل
- ✅ رفض action مجهول

## 9) ملفات تم تعديلها

| الملف | التغيير |
|---|---|
| `core/RichMessage.kt` | حقول `reactionOf`/`emoji` + actions `REACTION`/`REACTION_REMOVE` + تحقق |
| `core/database/Entities.kt` | `MessageReactionEntity` |
| `core/database/RedDao.kt` | استعلامات الـ reactions |
| `core/database/RedDatabase.kt` | version 2 + `REACTION_MIGRATION_1_2` |
| `core/database/LocalRepository.kt` | `applyReaction` + `reactionsForConversation` + حذف مع المحادثة |
| `core/RedConnectionService.kt` | `ReactionEventBus` + معالجة الاستقبال + `applyOutgoingReactionLocally` + 4 دوال إرسال |
| `ui/RedDashboard.kt` | `MessageReactions` + `ReactionEmojiBar` + state + تحميل/تحديث فوري |
| `test/.../RichMessageTest.kt` | 11 اختبار وحدة |

## 10) البوابة (Gate) — حالة الامتثال

| معيار البوابة | الحالة |
|---|---|
| Owner واضح | ✅ مطوّر يونس |
| Threat model مختصر | ✅ القسم 7 |
| API/Proto contract واضح | ✅ القسم 3 (RichMessage) — لا proto جديد |
| اختبارات success/failure | ✅ القسم 8 (11 اختبار) |
| تصميم RTL والوصولية | ✅ القسم 6 |
| telemetry/audit بدون تسريب خصوصية | ✅ لا telemetry — الإيموجي محلي فقط |
| build + runtime evidence | ⏳ ينتظر CI على جهازين فعليين (Alpha gate) |

> **ملاحظة runtime:** الميزة تنتظر اختبار runtime على جهازين فعليين وفق بوابة Alpha. التشفير والتدفق متسقان مع البنية الموجودة (Signal + Sender Keys) ولا يضيفان مساراً جديداً للخادم.
