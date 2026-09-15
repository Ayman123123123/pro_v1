# تفعيل GitLab للمشروع

أضيف ملف `.gitlab-ci.yml` إلى جذر المستودع. يشغّل خط أنابيب GitLab عند الدفع، وMerge Request، والتشغيل اليدوي، ويختبر backend ولوحة الإدارة وتطبيق Android.

## الصلاحيات المطلوبة

لا تُكتب صلاحيات الحساب أو مفاتيح الوصول داخل المستودع. من GitLab افتح:

1. **Project information > Members**: أضف المستخدم أو المجموعة بدور **Maintainer** لإدارة CI/CD والمتغيرات والفروع المحمية. استخدم **Owner** فقط على مستوى المجموعة عند الحاجة الإدارية الفعلية.
2. **Settings > CI/CD > Job token permissions**: فعّل السماح للمشروع بالوصول إلى المشاريع المطلوبة فقط. لا تستخدم allowlist مفتوحة إلا إذا كانت البنية تعتمد عليها صراحة.
3. **Settings > Repository > Protected branches**: احمِ `main`، واجعل الدمج يتطلب pipeline ناجحًا ومراجعة، واسمح بالدفع المباشر فقط للحسابات الإدارية.
4. **Settings > CI/CD > Variables**: أضف الأسرار كـ **Masked** و**Protected**. لا تضع token أو كلمة مرور أو ملف `.env` في YAML أو Git.
5. **Settings > General > Visibility**: اختر مستوى الظهور المناسب، وتحقق من أن Runner مسجل ومتاح للمشروع.

## تشغيل GitLab

- ادفع هذا المستودع إلى مشروع GitLab.
- تأكد من وجود Runner يدعم Docker executor.
- شغّل pipeline يدويًا من **Build > Pipelines > Run pipeline** عند الحاجة.
- إذا كان الدفع يتم من CI إلى مستودع آخر، أنشئ Project Access Token منفصلًا بصلاحية أقل ما يلزم، مثل `read_repository` أو `write_repository`، واحفظه في متغير محمي ومقنّع.

## ملاحظة

GitLab لا يملك مفتاحًا عامًا اسمه "كل الصلاحيات" لملف CI. صلاحيات العضوية، و`CI_JOB_TOKEN`، وProtected branches، وAccess Tokens إعدادات منفصلة. منح صلاحيات شاملة بلا تحديد يعرّض الشيفرة والأسرار للخطر، لذلك يحدد هذا الملف صلاحيات التنفيذ والـ artifacts فقط.
