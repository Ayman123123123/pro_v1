# الشجرة الميتة `app/` — لا تُحذف ولا تُبنى (DEAD TREE)

> **الخلاصة:** هذا المجلد (`app/`) **خارج Build graph تماماً**.
> الوحدة `:app` في Gradle **لا** تشير إليه — بل إلى `red-app/`:
>
> ```kotlin
> // settings.gradle.kts
> include(":app")
> project(":app").projectDir = file("red-app")
> ```
>
> أي أن كل ما تحت `app/src/...` (بما فيه `app/src/main/java/com/red/`
> ~ 52 ملف Kotlin) **لا يُجمَّع ولا يُشحَن في الـ APK** — بما فيه
> `RedSovereignApp` و `RedMainHost` و `MasterFeatureSet` وسائر النماذج
> الأولية القديمة.

## لماذا لا تُحذف؟

1. **إرث Signal الموروث**: `app/` يحمل شجرة Signal الأصلية
   (`org.thoughtcrime.securesms` + طبقات JNI/proguard/sampledata) —
   حذفها يمحو مصدر الاستخراج المرجعي.
2. **مصدر استخراج فقط**: الحزم `com.red.sovereign.*` هنا نماذج أولية
   سابقة لتوحيد 2026-08-19 (`docs/UNIFICATION_2026-08-19.md`)؛ كل ما له
   قيمة انتقل إلى `red-app/`، وما بقي هنا **تاريخ** لا كود حي.
3. **التدقيق**: وجود ~9 ملفات بعلامات `!!` و~33 بسطور إنجليزية خام هنا
   **لا يعيب المشحون** — الماسحات التي تخلط `app/` مع `red-app/` تُضلِّل
   نفسها؛ راجع قسم «كيف تُدقّق» أدناه.

## القاعدة الصارمة

- **ممنوع** على `red-app/` استيراد أي صنف موجود في هذه الشجرة فقط
  (42 رمزاً حصرياً: `RedSovereignApp`, `RedMainHost`, `MasterFeatureSet`,
  `RedVoipMaster`, `CallOrchestrator`, `SyncEngine`, `QuantumGuard`, … —
  القائمة الكاملة في `scripts/guard-dead-tree.ps1`).
- الحارس: `scripts/guard-dead-tree.ps1` (Windows) و
  `scripts/guard-dead-tree.sh` (CI/Linux) — يفشل (exit ≠ 0) عند:
  1. اختفاء التوجيه `project(":app").projectDir = file("red-app")` من
     `settings.gradle.kts`، أو
  2. وجود أي `import` في `red-app/src` لرمز حصري بالشجرة الميتة.
- شغّله قبل أي مراجعة تدقيق:
  `powershell -File scripts/guard-dead-tree.ps1`

## كيف تُدقّق المشحون (لا الشجرة الميتة)

- نطاق التدقيق الشحني = `red-app/src/**` + `backend-server/src/**` فقط.
- استثنِ `app/**` و `android/**` و `*.log` و `*.htm*` من عدّادات
  `!!` والإنجليزية الخام — كلها خارج الحزمة.

## النقل إلى `_dead/`؟

نُظر فيه ورُفض: نقل ~7000 ملف (Signal + الأصول) يُبطل مسارات موثقة
ومراجع استخراجية دون أي كسب بنائي (المجلد أصلاً خارج الـ graph).
الوضع الحالي — مجلد موثّق + حارس فشل — هو الخيار الآمن.
