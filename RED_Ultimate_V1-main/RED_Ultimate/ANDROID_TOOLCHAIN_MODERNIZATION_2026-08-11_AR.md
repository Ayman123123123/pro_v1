# تحديث منظومة بناء Android — قرار هندسي موحّد

**التاريخ:** 2026-08-11

## الرأي في تعديلات الجهاز المحلي

الفكرة العامة صحيحة: Java 21، Room، SQLCipher، Compose، وضرورة توحيد Kotlin/R8. لكن النص المنسوخ ليس ملف Gradle صالحاً للدمج للأسباب التالية:

1. يحتوي نسخاً متعارضة من Kotlin (`1.9.24` و`2.0.21`) وAGP وSDK وNDK وminSdk.
2. TOML يمنع تعريف المفتاح نفسه مرتين؛ وجود نسختين من `kotlin` أو `buildTools` خطأ parse، وليس اختياراً تلقائياً للأحدث.
3. `kotlinOptions` لا توضع داخل `compilerOptions`، ولا نحتاج الاثنتين.
4. `packaging.resources` تتبع `android`، ولا توضع داخل `kotlin` أو `compilerOptions`.
5. `[libs.androidx.room](http://...)` نص Markdown مشوّه وليس accessor في Kotlin DSL.
6. `kapt(...)` و`"kapt"(...)` تكرار للمعالج نفسه.
7. AGP 9 يوفّر Kotlin مدمجاً؛ إضافة `org.jetbrains.kotlin.android` إليه تعيد تعارض extension الذي تحذر منه وثائق Android الرسمية.
8. الرجوع إلى Kotlin 1.9 + Compose Compiler 1.5.15 يحل مشكلة مؤقتاً عبر إعادة المشروع إلى خط قديم، لكنه لا يعالج سبب عدم تطابق metadata.

## المصفوفة المعتمدة

| المكوّن | القرار | السبب |
|---|---:|---|
| compileSdk | 37 | Android 17 وCore 1.18، ومدعوم من AGP 9.2 |
| targetSdk | 37 | أحدث سلوك منصة؛ أضيفت صلاحية LAN الجديدة فعلياً |
| minSdk | 26 | يطابق التطبيق الفعلي قبل التعديل ولا يسقط أجهزة كانت مدعومة في canonical app |
| Build Tools | 36.0.0 | الإصدار الافتراضي/المدعوم رسمياً لـAGP 9.2 رغم compileSdk 37 |
| NDK catalog | 28.2.13676358 | الافتراضي الرسمي لخط AGP 9.2 |
| Gradle | 9.4.1 | المتطلب الرسمي الدقيق لـAGP 9.2، مع SHA-256 للـdistribution |
| AGP | 9.2.1 | يدعم API 37 ويحتوي إصلاح R8 RecordTag، ومثبت SHA-256 في المشروع |
| Kotlin/compiler plugins | 2.3.10 | الخط الذي حدّث إليه AGP 9.2؛ لا خلط 1.9/2.0/2.2 |
| Built-in Kotlin | مفعّل | المسار الرسمي في AGP 9؛ لا kotlin-android منفصل |
| KSP | 2.3.11 | أحدث إصدار مستقر بتاريخ المراجعة، وفيه إصلاحات AGP 9 built-in Kotlin |
| Room | 2.8.4 | أحدث Room مستقر؛ KSP بدل KAPT، وKTX صار ضمن runtime |
| kotlinx.serialization | 1.11.0 | أحدث مستقر مبني على Kotlin 2.3.20 ومتوافق مع خط metadata 2.3 |
| Compose BOM | 2026.06.01 | أحدث BOM مستقر موصى به رسمياً |
| Media3 | 1.11.0 | أحدث مستقر |
| WorkManager | 2.11.2 | أحدث مستقر |
| WebRTC SDK | 144.7559.09 | أحدث artifact معلن في مستودع المشروع الرسمي/Maven Central |
| SQLCipher Android | 4.17.0 | أحدث إصدار مستقر، موجود سابقاً |
| libsignal | 0.99.1 | حديث ومثبت SHA-256؛ 0.100.0 يفرض SPQR لكل الجلسات ويحتاج rollout بروتوكول لا version bump أعمى |
| Java/JVM target | 21 | موحّد عبر toolchain وcompileOptions وcompilerOptions واحدة |

### لماذا لم ننتقل إلى AGP 9.3.1 أو Kotlin 2.4.10 فوراً؟

- AGP 9.3.1 هو الأحدث، لكنه يتطلب Gradle 9.5 ويغيّر كامل بصمات build toolchain. الاتصال الحالي لا يستطيع تشغيل CI أو إعادة توليد SHA-256 بسبب صلاحية GitHub `workflows` والشبكة المحلية المقيدة.
- Kotlin 2.4.10 هو الأحدث، لكن جدول JetBrains يضع دعمه الكامل حتى AGP 9.1؛ بينما API 37 وCompose الحديث يحتاجان AGP 9.2 على الأقل في هذا المشروع.
- لذلك اختير أحدث خط **متوافق ومثبت حالياً** بدلاً من أعلى أرقام غير قابلة للإثبات. بعد عودة CI يمكن ترقية AGP 9.3.1 كمرحلة مستقلة مع تجديد verification metadata.

## التغييرات المنفذة

1. حذف KAPT من canonical app واستبداله بـKSP لRoom.
2. تفعيل AGP built-in Kotlin وإزالة opt-out القديم وDSL القديمة.
3. توحيد Java/Kotlin على JVM 21 عبر toolchain واحد.
4. تحويل قيم min/target SDK إلى version catalog؛ لا أرقام مكررة داخل module.
5. تنظيم `packaging.resources` مرة واحدة داخل `android`.
6. إضافة Kotlin BOM بجانب Compose BOM لتقليل انحراف runtime.
7. رفع Room وMedia3 وWorkManager وlibsignal المختار.
8. إضافة `ACCESS_LOCAL_NETWORK` وتصريح runtime قبل login/discovery لتوافق targetSdk 37.
9. إبقاء release TLS-only وإضافة network security overlay يسمح بعنوان LAN المتغيّر في debug فقط.
10. نقل SVG الخام من `res/mipmap` إلى `red-app/artwork`؛ Android يعبّئ VectorDrawable/PNG الموجودة ولا يقبل SVG خاماً في AAPT.
11. تثبيت صورة Android Docker بالـdigest واستخدام صورة تحتوي JDK 21، ثم تثبيت API 37 وBuild Tools 36.
12. إضافة Android unit/assemble job إلى quality gate.
13. إضافة حراس آلية إلى فاحص Android لمنع رجوع خلط الإصدارات وKAPT وSVG وغياب صلاحية LAN.
14. إزالة `buildscript classpath` المكرر (AGP/Wire/Safe Args) والاعتماد على Plugins DSL فقط.
15. جعل composite `build-logic` و`:fast-lint` يُحمّلان فقط عند طلب QA/CI؛ أما sync و`assembleDebug` فلا يدفعان كلفة أو تعارض أدوات Signal القديمة.
16. تهيئة SQLCipher native صراحةً قبل أن ينشئ Room قاعدة البيانات؛ كانت أول عملية فتح معرضة لـ`UnsatisfiedLinkError`.
17. إضافة `media3-transformer` الذي كانت الشيفرة تستورده بلا dependency، وترحيلها إلى `EditedMediaItem` وجعل ضغط 720p حقيقياً بدلاً من تغيير MIME فقط.
18. استبدال `gradle-wrapper.jar` وسكربتي `gradlew` القديمين (كان JAR فعلياً من Gradle 7.4.2) بنسخ 9.4.1 الرسمية بعد مطابقة SHA-256، مع تثبيت checksum للـdistribution أيضاً.
19. إزالة `@Composable` المكررة على `StickerMessage`؛ Compose annotation ليست repeatable وكان الخطأ مؤكداً.
20. إزالة سطر `fun open(story)` المكرر داخل `StoryViewModel` الذي كان يخلّ بتوازن البنية ويولّد cascade أخطاء parser بعيدة عن المصدر.
21. إعلان Compose/Fragment/Lifecycle/Media3/SQLite APIs المستخدمة كاعتمادات مباشرة بدلاً من الاتكال على transitives قابلة للتغيّر.
22. إضافة فحص موارد يطابق 160 مرجعاً محلياً مع تعريفاتها، مع استثناء `android.R` الصحيح، وفحص يمنع تكرار annotations والتصريحات مستقبلاً.

## علاقة ذلك بأخطاء D8/R8

أخطاء Kotlin metadata لا تُحل بإضافة `packaging.excludes` مراراً؛ تلك الاستثناءات تخص موارد META-INF ولا تغيّر bytecode metadata. المعالجة الصحيحة هي:

- compiler plugins على خط Kotlin واحد؛
- AGP/R8 متوافق مع Gradle وAPI؛
- إزالة KAPT غير المتوافق مع built-in Kotlin؛
- استخدام KSP2 وRoom حديث؛
- عدم تطبيق Compose Compiler 1.5.15 مع Kotlin 2.x؛
- عدم إدخال SVG خام إلى AAPT.

## ما يلزم لإغلاق المهمة 100%

على جهاز متصل بعد إعادة منح GitHub صلاحية workflows:

```bash
cd RED_Ultimate_V1-main/RED_Ultimate
./gradlew :app:testDebugUnitTest :app:assembleDebug \
  -PRED_SKIP_BUILD_LOGIC=true \
  --dependency-verification strict \
  --no-daemon --stacktrace

# بعد نجاح أول build متصل، استبدال bootstrap trust ببصمات فعلية:
./gradlew --write-verification-metadata sha256 \
  :app:testDebugUnitTest :app:assembleDebug \
  -PRED_SKIP_BUILD_LOGIC=true --no-daemon
```

لا يجوز إعلان نجاح APK قبل تنفيذ الأمر الأول فعلياً. الفحوص الساكنة تثبت صحة البنية والـDSL والعقود، لكنها لا تستبدل D8/R8/AAPT الحقيقي.
