# فحوص CI مقترحة — تحتاج تطبيقًا يدويًّا منك

## لماذا هي هنا لا في مكانها

محاولة دفع `.github/workflows/blank.yml` رُفضت من GitHub:

```
refusing to allow a GitHub App to create or update workflow
`.github/workflows/blank.yml` without `workflows` permission
```

هذا قيد صلاحيات في تطبيق GitHub المستعمل في هذه الجلسة، لا خطأ في
الملف. فحُفظ المحتوى هنا ليُطبَّق بيدك.

## لماذا يستحق التطبيق

`.github/workflows/blank.yml` الحالي **قالب افتراضي لا يفحص شيئًا**:

```yaml
- name: Run a one-line script
  run: echo Hello, world!
```

ولهذا كان فحص `build` في PR #46 يظهر **أخضر** بينما تطبيق الأندرويد
**لا يُترجَم أصلًا**: كان في `RedDashboard.kt:569` سطر فيه `remembr`
(مرجع غير قابل للحلّ) ومتغيّر `messageText` معلَن مرتين — وكلاهما
يُفشل ترجمة الوحدة كاملةً، وبقيا منذ commit الأساس `69277ec`.

**فحصٌ أخضر كاذب أسوأ من غياب الفحص**، لأنه يمنح ثقة بلا أساس.

## كيف تطبّقها

```bash
cp ci-proposed/ci-workflow.yml .github/workflows/blank.yml
git add .github/workflows/blank.yml
git commit -m "ci: فحوص تُنفَّذ فعلًا بدل قالب Hello world"
git push
```

## ما تفعله المهام الأربع

| المهمة | ما تفحصه | يحتاج JDK |
|---|---|---|
| `kotlin-structure` | الفاحص البنيوي: أقواس، تعريف مكرّر، أخطاء إملائية، مراجع مؤرشفة | لا |
| `admin-dashboard` | `tsc --noEmit` + الحرّاس الأربعة + `build` | لا |
| `gradle-server` | اختبارات الخادم (284 `@Test`) | نعم |
| `gradle-app` | اختبارات الأندرويد (253 `@Test`) + رفع التقارير عند الإخفاق | نعم |

**ملاحظة تقنية مهمّة:** `backend-server` مشروع Gradle **مستقل** له
`settings.gradle.kts` وwrapper خاصّان، وليس وحدةً في البناء الجذري
(الجذر يعرّف `:app` و`:shared-proto` فقط). لذلك تُشغَّل اختباراته من
مجلّده بـ`./gradlew test` لا بـ`./gradlew :backend-server:test` —
الثاني يخفق بـ`project not found`.

## الأهمّ

مهمّتا Gradle هما **الموضع الوحيد** الذي تُترجَم فيه شيفرة Kotlin
وتُشغَّل اختباراتها: بيئة التطوير في هذه الجلسة بلا JVM (لا `java`،
ولا صلاحية تثبيت، ولا شبكة). فحتى تُطبَّق هذه المهام، تبقى تغييرات
Kotlin **غير مُترجَمة** ويلزم تشغيلها يدويًّا:

```bash
cd RED_Ultimate_V1-main/RED_Ultimate/backend-server && ./gradlew test
cd RED_Ultimate_V1-main/RED_Ultimate && ./gradlew :app:testDebugUnitTest
```
