# توثيق تحقق Android — 21 أغسطس 2026

## النتيجة التنفيذية

تمت معالجة عائق الاعتمادات الذي كان يوقف المعالجة، وتأكد أن ملفات `libsignal-android:0.86.5` و`libsignal-client:0.86.5` مكتملة في Gradle cache. كما تأكد تحميل معالج Room عبر KSP بنجاح. عاد تعطل البناء الكامل في شجرة العمل الأصلية لاحقاً إلى مراجع مصدر غير مكتملة ضمن DINSTAR/PSTN/SMS، وهي مكونات استبعدها نطاق العمل صراحةً، وإلى configuration cache متسلسل تالف عند تشغيل build آخر مع إعادة استخدامه.

لتحقق جودة التحسينات الواقعة ضمن النطاق المسموح، تم إنشاء نسخة Git تحقق معزولة خارج مجلد المشروع الأصلي. تضمنت نسخة التحقق جميع تعديلات `red-app` المحلية ثم أُعيدت مكونات DINSTAR وPSTN وSMS ومسار PSTN في `IncomingCallActivity` إلى أساس Git داخل تلك النسخة وحدها. لم يُعدّل أي ملف مصدر في شجرة العمل الأصلية لهذا الغرض.

| بوابة التحقق | النتيجة | الدليل |
|---|---:|---|
| اكتمال اعتمادات Signal | ناجح | `libsignal-android` بحجم 128,538,524 بايت و`libsignal-client` بحجم 105,854,021 بايت في cache |
| تشغيل Room KSP | ناجح في بيئة التحقق | مرّت مهمة `:app:kspDebugKotlin` قبل تجميع Kotlin |
| تجميع Kotlin للنطاق المسموح | ناجح | إعادة تشغيل `:app:compileDebugKotlin` في بيئة Gradle معزولة بلا مؤشرات فشل وبمخرجات نجاح |
| اختبارات وحدة Android للنطاق المسموح | ناجح | 50 حزمة، 221 اختباراً، 0 فشل، 0 خطأ، 0 متجاوز |
| اختبارات الخادم الكاملة | ناجح سابقاً | `BUILD SUCCESSFUL` وفق تقرير الجولة |
| فحص لوحة الإدارة | ناجح سابقاً | `npm run check` ناجح وفق تقرير الجولة |

## العائق في شجرة العمل الأصلية

البناء الكامل في شجرة العمل الأصلية لا يجتاز بوابة Android حالياً، لكنه لا يفشل بسبب تحسينات الحالات أو المنشورات أو المؤتمرات. تظهر الأخطاء في ملفات ومراجع محلية مرتبطة بالمسارات المستبعدة:

| المسار | الخطأ المرصود | التصنيف | الإجراء في هذه الجولة |
|---|---|---|---|
| `features/dinstar/DinstarViewModel.kt` | مراجع `PstnIncoming` و`IncomingCallActivity` غير محلولة | DINSTAR/PSTN مستبعد | لم يُعدّل |
| `features/sms/PstnEventSocket.kt` | استخدام `toJsonElement` غير متوافق مع واجهة التسلسل الحالية | PSTN/SMS مستبعد | لم يُعدّل |
| `features/sms/SmsViewModel.kt` و`SmsConversationsScreen.kt` | عدم اتساق دالة `openNewChat` واستدعاء `suspend` خارج coroutine | SMS مستبعد | لم يُعدّل |
| `calls/IncomingCallActivity.kt` | الاستدعاء `pstnSocket.send` يعتمد على تعديل PSTN المستبعد | PSTN مستبعد | لم يُعدّل |

كما ظهر في تشغيل مستقل مع configuration cache مفعّل خطأ `StreamCorruptedException: unexpected EOF in middle of data block` ضمن `:app:kspDebugKotlin`. لا يمثل ذلك خطأ KSP أو Room أو Signal؛ إعادة التشغيل مع `--no-configuration-cache` تجاوزت هذا العائق ووصلت إلى تجميع Kotlin الفعلي.

> لا يجوز اعتبار بناء Android الأصلي كاملاً ناجحاً قبل أن يقرر مالك المشروع معالجة أو عزل التناقضات الموجودة في DINSTAR/PSTN/SMS. لم يتم إصلاحها التزاماً بالنطاق الصريح، لكن نجاح نسخة التحقق يثبت أن تحسينات هذه الجولة تجتاز التجميع واختبارات الوحدة بعد استبعاد تلك المكونات فقط.

## أوامر تحقق موصى بها

لإعادة تحقق نطاق العمل الحالي من دون configuration cache المتضرر، تُستخدم الأوامر التالية من جذر `RED_Ultimate`:

```powershell
$gradleArgs = @(
  '-Djava.net.preferIPv4Stack=true',
  '-Dorg.gradle.cache.internal.locklistener.port=0',
  ':app:testDebugUnitTest',
  '--max-workers=1',
  '--no-daemon',
  '--console=plain',
  '--no-configuration-cache'
)
& .\gradlew.bat @gradleArgs
```

تظل هذه الأوامر متوقفة على معالجة المصدر في المكونات المستبعدة إذا نُفذت على شجرة العمل الأصلية كاملة.

## ملفات دعم

| الملف | الغرض |
|---|---|
| `docs/diagnostics/signal-cache-inventory.txt` | توثيق وجود ملفات Signal وأحجامها |
| `docs/diagnostics/android-gradle-daemon-44988-tail.log` | سجل فشل تجميع مصدر DINSTAR/PSTN/SMS في الشجرة الأصلية |
| `docs/diagnostics/android-excluded-component-diff.patch` | فرق المكونات المستبعدة المصنفة أثناء التحقق |
| `docs/diagnostics/working-tree-inventory.txt` | جرد واسع للفروق المحلية قبل أي دمج أو نشر |

**الحالة:** لم تكتمل بوابة Android الأصلية بسبب النطاق المستبعد، فيما اجتازت نسخة التحقق المعزولة تجميع Kotlin واختبارات الوحدة الكاملة لنطاق التحسينات المسموح.
