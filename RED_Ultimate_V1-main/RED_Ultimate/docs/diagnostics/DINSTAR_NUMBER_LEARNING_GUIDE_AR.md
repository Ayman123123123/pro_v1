# دليل تعلّم أرقام الشرائح — DINSTAR UC2000-VE (Number Learning)

> **الغاية:** حل مشكلة `sim_number_masked` الفارغة في كل منافذ Sabafon الثمانية، وتمكين عرض الرقم وربط SIM↔مستخدم بشكل موثوق في لوحة RED Ultimate.

---

## 1) ما هو Number Learning ولماذا نحتاجه؟

- بوابة UC2000-VE تقرأ تلقائيًا `IMSI/ICCID/Registration/Signal` عبر `get_port_info`، لكنها **لا تملأ حقل `number` (MSISDN) تلقائيًا** عند Sabafon اليمنية.
- النتيجة في RED Ultimate: كل `port.number` و `port.numberMasked` يظهران فارغَين / `غير معروف` رغم أن `REGISTER_OK` والإشارة ‎-65 إلى ‎-81 dBm (الفحص الحي 2026-08-23: كل 8 شرائح REGISTER_OK مربوطة 0→712065805 … 7→712065586 لكن اللقطات بلا رقم).
- السبب ليس عطلًا في Backend — جدول `telecom_gateways` و `gateway_port_snapshots.sim_number_masked` يعكسان ما تُرجعه البوابة؛ البوابة نفسها هي التي تترك الحقل فارغًا حتى تُفعَّل ميزة **Phone Number Learning**.
- بعد التفعيل تتولى البوابة استخراج الرقم بنفسها بإحدى طرق USSD/SMS/Call مع **كلمة مفتاحية (Keyword)**، ثم تظهر `sim_number_masked` ويُعبأ `number` في `fleet/ports` ويصبح `GET /api/admin/dinstar/bindings/reconcile` يعرض `liveNumber` مملوءًا.

[صورة: Diagnostics → Dinstar Fleet → كل المنافذ تُظهر «غير معروف» في حقل الرقم رغم REGISTER_OK]

### لماذا لا يستطيع الـ Backend تفعيله برمجيًا؟

- حسب دليل Dinstar الرسمي §4.10.3 فإن **USSD mode** هو إعداد في واجهة الويب للجهاز: "send USSD to carrier and get response, e.g. send `*156#`, get `Your number is: 8618344144906`, configure Keywords to `Your number is:` — this is a **DEVICE WEB UI setting**, not an HTTP API call".
- لا يوجد في HTTP API الموثق (get_port_info / set_port_info / send_ussd / send_sms / get_cdr) أي endpoint لتشغيل Number Learning — و `send_ussd` يرسل USSD لمرة واحدة ويعيد النص، لكنه لا يخزّن الرقم في حقل المنفذ. التعلم يجب تكوينه يدويًا من واجهة الجهاز ثم يحفظه الجهاز داخليًا.

---

## 2) المسار الدقيق في واجهة الويب

```
Human Behavior → Phone Number Learning
```

> في بعض إصدارات firmware يظهر تحت `Mobile Configuration → Phone Number Config` لكن المرجع الكامل للجهاز (UC2000-VE_Complete_Reference.md §7/§17) يثبته تحت **Human Behavior → Phone Number Learning**. إن لم تجده في موضعه، ابحث عن نفس الاسم في القائمة اليسرى.

[صورة: القائمة اليسرى → Human Behavior → Phone Number Learning]

### ضبط عام قبل أي نمط

| الحقل | القيمة المقترحة | ملاحظة |
|---|---|---|
| Enable | Yes | يفعّل التعلّم لكل المنافذ المحددة |
| Ports | 0–7 (كل المنافذ الثمانية) | يمكن اختيار منافذ بعينها للاختبار أولًا |
| Interval / Retry | اترك الافتراضي (مثل 60 دقيقة / 3 محاولات) | لا تُقصّره كثيرًا كي لا تُغرق الشبكة |

---

## 3) الأنماط الثلاثة — شرح عملي مع Sabafon اليمن

### أ) USSD (قراءة الرقم من ردّ USSD + كلمة مفتاحية)

المبدأ (نص الدليل): ترسل البوابة كود USSD إلى المشغّل، فيرد برسالة تحوي الرقم، فتستخرجه البوابة بالكلمة المفتاحية.

| الحقل | مثال عام من الدليل | مثال Sabafon (اليمن) |
|---|---|---|
| USSD Code | `*156#` | جرّب `*121#` (قائمة الباقات) أو `*100#` (الرصيد) — **لا يوجد كود USSD موثق علنًا لمعرفة رقمي في Sabafon** |
| Keywords | `Your number is:` | إن كان الرد يحوي `رقمك هو` أو `Your number is` ضع نفس العبارة حرفيًا كـ Keyword |
| استخراج الرقم | الأرقام بعد الـKeyword مباشرة | `... رقمك هو 712065805 ...` → يستخرج `712065805` |

**خطوات USSD:**
1. اختر Mode = **USSD**
2. أدخل `USSD Code` (ابدأ بـ `*121#` للتجربة)
3. أدخل `Keywords` (انسخ العبارة التي تسبق الرقم في ردّ المشغّل بدقة، مع المسافة والنقطتين)
4. احفظ (Save/Apply)

[صورة: Phone Number Learning → Mode: USSD → حقول USSD Code + Keywords + Ports]

> **تحذير Sabafon:** أكواد `*100#` تعيد الرصيد لا الرقم. إن لم يرجع `*121#` رقم المتصل، فالنمط USSD غير مجدٍ لمعرفة الرقم في Sabafon — استخدم نمط **Call** أدناه (موصى به).

### ب) SMS (إرسال SMS للمشغّل وقراءة الرد)

- Mode = **SMS**
- ترسل البوابة رسالة نصية إلى رقم خدمة المشغّل، فيرد المشغّل برسالة تحوي الرقم، وتستخرجه بنفس آلية Keyword.
- مثال تخيلي: Text = `NUM` إلى `121` والـKeyword = `Number:` — لكن Sabafon لا تعلن رقم SMS لمعرفة رقمي، لذا هذا النمط نادرًا ما يفيد في اليمن.

[صورة: Phone Number Learning → Mode: SMS → حقول SMS Number + SMS Text + Keywords]

### ج) Call (موصى به — يعمل مع كل شبكات اليمن وبدون تكلفة خارجية) ⭐

**المبدأ:** منفذ **A** يتصل برقم منفذ **B** داخل نفس البوابة؛ البوابة تلتقط **CLIP (رقم المتصل)** للطرفين تلقائيًا وتخزّنه كرقم لكل شريحة. لا يحتاج USSD ولا Keyword ولا يعتمد على خدمة المشغّل.

**لماذا هو الأنسب لأسطول 8 شرائح على جهاز واحد؟**
- لا يستهلك رصيدًا خارجيًا (المكالمة داخلية بين منفذين على نفس الجهاز — تُحتسب داخلية أو تُلغى بعد التقاط CLIP).
- لا يحتاج معرفة أكواد Sabafon.
- يتعلم **8 أرقام دفعة واحدة** بحلقة اتصالات متبادلة دون تدخل يدوي بعد الإعداد.

**خطوات Call:**
1. Mode = **Call**
2. حدد Ports = 0–7
3. فعّل **Auto CLIP Learning** إن وُجد كخيار منفصل (بعض firmware يسميه `Call Mode` فقط)
4. احفظ — ستبدأ البوابة حلقة: `0→1`, `2→3`, … ثم تتبادل، وخلال دقائق تمتلئ حقول الأرقام.

[صورة: Phone Number Learning → Mode: Call → اختيار المنافذ 0-7 → زر الحفظ]

> إن كان firmware يعرض خيار **Call + Keyword** فاترك Keyword فارغًا في نمط Call — CLIP لا يحتاج استخراج نص.

---

## 4) ميزتان مرتبطتان يُستحسن تفعيلهما معه

| الميزة | المسار | ماذا تفعل | لماذا نفعّلها |
|---|---|---|---|
| **Auto CLIP Routing** | `Call Configuration → Service Parameter` أو `Human Behavior` حسب الإصدار | توجّه المكالمة الواردة فورًا عبر CLIP بدون رسالة "Please dial the extension" | كانت سبب تأخر الاستقبال — فعّل `Do Not Answer GSM Incoming Call for Hotline = Yes` |
| **Auto Update SIM Number** | `Human Behavior → Phone Number Learning` (خيار فرعي) أو `Mobile Configuration → Port Parameter` | تحدّث الرقم تلقائيًا عند تبديل الشريحة | يمنع بقاء رقم قديم بعد تبديل SIM |

[صورة: Service Parameter → Do Not Answer GSM Incoming Call for Hotline = Yes]

---

## 5) التحقق بعد التعلّم

### أ) من واجهة الجهاز
`System Information → Mobile Information` أو `Port Parameter` — يجب أن يظهر عمود `Phone Number` مملوءًا (مثل `712065805`).

[صورة: Mobile Information بعد التعلم — عمود Phone Number مملوء]

### ب) من لوحة RED Ultimate (موثوق)

1. افتح `إدارة الأسطول — DINSTAR` → اضغط **تحديث**.
2. اضغط **تحقق من الربط** (يستدعي `GET /api/admin/dinstar/bindings/reconcile`).
3. النتيجة المتوقعة بعد نجاح التعلم:

```json
{
  "ports": [{
    "index": 0,
    "host": "192.168.11.2",
    "liveNumber": "712065805",
    "liveNumberMasked": "••••5805",
    "needsNumberLearning": false,
    "mismatch": false,
    "reason": "Bound number matches live SIM number"
  }],
  "summary": { "totalPorts": 8, "boundPorts": 8, "ok": 8, "mismatched": 0 }
}
```

- قبل التعلم كان `liveNumber: null` و `needsNumberLearning: true` و `reason: "Live number blank (Sabafon) — fallback IMSI last4=... needs Phone Number Learning"` ويظهر في الواجهة وسام برتقالي **"يحتاج تعلّم الرقم"** مع رابط لهذا الدليل.
- الـ endpoint البديل `GET /api/admin/dinstar/bindings/discover` يعيد نفس البيانات مضافًا إليها `learnable` و `learnInstruction` و `anyLearnable` — حيث `learnInstruction` هو:
  > `Dinstar UI: SIM Settings > Phone Number Learning > Mode=USSD/SMS/Call with keyword extraction (Sabafon: *100# is balance, *121# bundle menu — if USSD own-number unknown use missed-call loop fallback).`

[صورة: لوحة RED → زر «تحقق من الربط» → جدول المطابقة يظهر liveNumber مملوءًا و needsNumberLearning = false]

### ج) من الـ API مباشرة (للمطور)

```bash
curl -H "Authorization: Bearer $TOKEN" https://younes.example/api/admin/dinstar/bindings/reconcile | jq .summary
curl -H "Authorization: Bearer $TOKEN" https://younes.example/api/admin/dinstar/bindings/discover  | jq '.ports[] | {index, liveNumber, needsNumberLearning}'
```

---

## 6) استكشاف الأخطاء

| العَرَض | السبب المحتمل | الحل |
|---|---|---|
| بعد الحفظ لا يزال `liveNumber` فارغًا | المنافذ غير مسجلة (UNREGISTERED) أو بلا إشارة | تأكد `REGISTER_OK` و `signalUsable=true` قبل التعلم؛ حسّن موقع الهوائي |
| USSD يعيد `Unknown USSD code` | الكود غير مدعوم في Sabafon | جرّب Call mode بدلًا منه |
| Keyword لا يستخرج الرقم | العبارة لا تطابق الرد حرفيًا (مسافة/نقطتان) | انسخ الرد كما هو من `SMS and USSD → USSD` ثم الصق Keyword مطابقة |
| Call mode لا يتعلم | المنافذ مشغولة بمكالمات | أوقف الاختبارات وأعد المحاولة بعد دقائق؛ راجع CDR |
| رقم قديم يبقى بعد تبديل SIM | Auto Update SIM Number معطّل | فعّلها واحذف الربط القديم ثم أعد reconcile |

---

## 7) ملاحظات ختامية

- التعلم إعداد **لمرة واحدة لكل شريحة**؛ بعد حفظ الأرقام تبقى حتى تبديل الشريحة.
- لا حاجة لإعادة التعلم عند إعادة تشغيل البوابة — الأرقام محفوظة في ذاكرة الجهاز.
- إن أضفت بوابة جديدة للأسطول، كرر نفس الخطوات عليها ثم استخدم `POST /api/admin/dinstar/bindings/bulk` لربط 8 مستخدمين دفعة واحدة.

**مراجع:** دليل Dinstar الرسمي §4.10.3 (USSD/SMS/Call + Keyword)، و `UC2000-VE_Complete_Reference.md §17 Human Behavior → Phone Number Learning`، ووثائق `docs/05-DINSTAR-UC2000-VE-8G.md` و `DINSTAR_OFFICIAL_RESEARCH_NOTES_2026-08-21_AR.md`.

---

*آخر تحديث: 2026-08-23 — RED Ultimate V35 — أسطول DINSTAR UC2000-VE-8G @ 192.168.11.2*
