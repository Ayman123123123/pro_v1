# تقرير القبول النهائي — Definition of Done (§7) — 2026-09-15

> الجلسة: `arena/01a0a14b-pro-v1` — تحقق سكوني شامل (لا JDK/SDK/Docker هنا:
> البناء والاختبار التنفيذي والقياسات الميدانية بانتظار المالك/CI).

## 1) الحكم على بنود §7 الثمانية

| # | البند | الحكم | الدليل / المتبقي |
|---|---|---|---|
| 1 | رسائل 1:1 ومجموعات لحظيًا + بعد القتل | ⏳ بانتظار جهازين | الكود: Placeholder + `DecryptedMessageBus` + `MessageSendErrorBus` + مضخة التسليم + `pendingFor` (P1+P9) — يحتاج تجربة المالك |
| 2 | رنين <2s من مقتول (Pixel/Samsung/Xiaomi) | ⏳ بانتظار أجهزة | السلسلة كاملة سكونيًا: `UnifiedPushSender.send` ← `tokensFor` ← `POST /api/devices/push-token` ← `RedPushService` ← CallStyle/FSI — القياس على عتاد حقيقي |
| 3 | صوت 1:1 على 100kbps + فيديو 480p→720p | ⏳ بانتظار Throttling | Opus/FEC/DTX + `AdaptiveCallQuality` + `RedBundledTones` + مهلة 45s (`CallRingPolicy`) حاضرة — يحتاج شبكة مُخنَّقة |
| 4 | مجموعة-8 (10د) + بث 1000 + مساحة 500 | ⏳ بانتظار حمل | SFU-first + `sfuCanProduce` + أدوار التذاكر + `MUTE_ALL` + سقف 12 بلاطة (أُضيف في هذا المسح) — الحجم لا يُختبر بلا حمل |
| 5 | صفر PSTN/Dinstar | ✅ يمر (بعد V52) | صفر Kotlin؛ V52 تُسقط 35 جدولًا + 5 عروض + 5 أعمدة `users`؛ البقايا: 4 تعليقات حذف + مفاتيح purge وظيفية + تعليق SQL عتيق (موثق §4) |
| 6 | صفر Firebase + دفع بلا جوجل | ✅ يمر | صفر حقيقي (ضجيج `disappearingMs`/`paradigm` فقط)؛ لا `google-services`؛ الدفع UnifiedPush ذاتي + ntfy في Compose |
| 7 | `app/` محذوف + `red-app` الوحيد | ✅ يمر | لا `app/`؛ `:app`→`red-app` في settings؛ CI يبني `:app`؛ الأصوات + إسناد GPL في `red-app/README.md` |
| 8 | CI واحد أخضر + صفر PRs قديمة | ⏳ دفع + قرار مالك | ملف واحد + أرشفة + مصفوفة env (P10) — «أخضر» يتطلب الدفع؛ PRs: #55 فقط (مُفرز، يحتاج قرار دمج/إغلاق بعد دفع المراحل) |

## 2) تحقق مصنوعات المراحل (سكوني)

| المرحلة | العينة المفحوصة | النتيجة |
|---|---|---|
| 0 | `derivedStateOf` في القوائم | ✅ |
| 1 | `PendingDecryptPlaceholder` + `DecryptedMessageBus` (الاسم الفعلي — الخطة كتبت `...UpdateBus`) + `MessageSendErrorBus` + `ChatsScreen` في الأرشيف بلا نسخة حية | ✅ |
| 2 | `RedPushService` + `connector:3.3.5` + `UnifiedPushSender` + ntfy + سلسلة `tokensFor→send` | ✅ |
| 3 | `RedBundledTones` + `RINGING`/`BUSY`/`HOLD` + `UNANSWERED_TIMEOUT_MS=45s` | ✅ |
| 4 | `GROUP_CALL_INVITE` + `GROUP_CALL_KICK`/`MUTE_MEMBER` (طرفان) + `callId`/`callIsVideo` في `RichMessage` | ✅ |
| 5 | `SCREEN_SHARE` + قفل الغرفة + **423** خادم (`ConferenceController:305`) | ✅ |
| 6 | لا `*gift*` + `VIEWER_NEEDS_MESH` + `setConsumerPreferredLayers` + Egress مؤجل موثق | ✅ |
| 7 | `sfuCanProduce` + `APPROVE_SPEAKER`/`DEMOTE_LISTENER` + `MUTE_ALL` + سقف البلاطات (**أُضيف الآن**) | ✅ بعد الإصلاح |
| 8 | V52 تُغلق النطاق (كانت الهجرات تُنشئ جداول ميتة في كل تثبيت جديد) | ✅ بعد الإصلاح |
| 9 | وثيقة العقود + 7 اختبارات + الكود (تسلسل/تثبيت/جلسات/TTL) | ✅ (نُفذت هذه الجلسة) |
| 10 | CI واحد + أرشيفان + compose لكل بيئة | ✅ (نُفذت هذه الجلسة) |
| 11 | `app/` محذوف + 5 أصوات + إسناد GPL | ✅ |
| §6 | صفر SDK خارجي (الضجيج: `amplitude` صوتية + `*Entry` واجهات) + `red.ly` سيادي | ✅ بعد مرآة Vosk |

## 3) إصلاحات هذا المسح (3)

1. **`V52__Drop_Cancelled_Pstn_Dinstar_Scope.sql`** — تُسقط 35 جدولًا + 5 عروض + 3 فهارس + 4 قيود + 5 أعمدة `users` من النطاق الملغي. التغطية مفحوصة برمجيًا (35/35 بلا زيادة ولا نقصان). الأمان: صفر مراجع Kotlin، الكيان نظيف، كل الـ FK داخلية، العروض الحية تقرأ `users`/`call_history` فقط، `CASCADE` لا يطال إلا الميت.
2. **`MAX_VIDEO_TILES = 12` + عدّاد `+N`** (`ConferenceService`/`ConferenceOverlay`) — شبكة الفيديو كانت بلا سقف (خطر OOM في المؤتمرات الكبيرة). ملاحظة: السقف على الـ renderers؛ ترشيد الـ consumers في SFU (active-speaker paging) متابعة لاحقة مقترحة.
3. **مرآة Vosk ذاتية** (`VoskTranscriber`) — ملف `vosk_mirror_url.txt` الاختياري يتجاوز عنوان المنبع؛ التمهيد المسبق كان وما زال يتجاوز التنزيل أصلًا؛ الاستدلال دون شبكة دائمًا.

## 4) انحرافات مقبولة (موثقة، ليست عيوبًا)

- **4 تعليقات حذف PSTN** في Kotlin + **مفاتيح `pstn_*` في `TokenStore`** (تطهير وظيفي لتثبيتات قديمة — حذف الأسماء يكسر الترقية) + **تعليق SQL عتيق** في V50 (الهجرات المطبقة لا تُعدَّل).
- **`red.ly`** في روابط الدعوة — صيغة سيادية (النطاق تشغيل، لا تبعية API).
- **جداول V15** (`encryption_sessions`…) بلا مراجع كودية لكنها خارج النطاق الملغي — تُركت لفرز المالك.
- **أسماء الخطة**: `DecryptedMessageBus` (لا `...UpdateBus`) — الكود هو المرجع.

## 5) على المالك (لا يُنجز من هنا)

1. **الدفع** (ممنوع حتى الإذن) → أول تشغيل أخضر للـ CI → تفعيل required-checks (التعليمات في تقرير P10 §5).
2. **حماية `main`** (403 على التوكن الحالي — التعليمات في تقرير P10 §5).
3. **قرار #55**: دمج/إغلاق بعد دفع المراحل 8–10 (مُفرز ومعلَّق عليه).
4. **اختبارات الأجهزة**: البنود 1–4 (جهازان + قتل + Throttling + حمل) — قائمة الاختبار في §1.
5. **فرز V15**: إحياء الجداول الأربعة أو إسقاطها بهجرة لاحقة.
