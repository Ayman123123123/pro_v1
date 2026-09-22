# خطوات GitHub التالية — 2026-09-15 (واجهة فقط)
> بلا أوامر طرفية — كل الخطوات من المتصفح + واجهة المحرر.

## 1) مراجعة CI للكوميت c272d69
- افتح صفحة المستودع ← تبويب Actions ← ابحث عن تشغيل الكوميت c272d69.
- افتح التشغيل وتحقق أن الوظائف الخمس خضراء وأن الـ artifacts مرفوعة.
- إن كان أحمر افتح Job الفاشل وانسخ مقتطف الخطأ من مخرجات الـ check.

## 2) حسم PR رقم 55
- افتح Pull requests ← رقم 55 ← تبويب Checks ثم Files changed.
- إن كان أخضر ومحدّثًا على main: اختر Squash and merge ثم Confirm.
- إن كان أحمر أو متعارضًا دلاليًا مع المراحل 8-10: اكتب تعليق فرز ثم Close.

## 3) تفعيل حماية main + الفحوص المطلوبة
- افتح Settings ← Branches ← Add rule للفرع main.
- فعّل: طلب مراجعة (1) + إلغاء الموافقات القديمة + منع التجاوز + منع الحذف والدفع القسري.
- بعد أول تشغيل أخضر فعّل Require status checks مع:
- Infrastructure and Docker / Backend tests (JDK 21) / Admin and app contract integration / Android 17 build and unit tests / Media SFU install and syntax.
- فعّل Require branches to be up to date ثم Save changes.

## 4) حذف الفروع المحلية المؤرشفة
- من الشريط الجانبي للمحرر ← إدارة الفروع ← احذف نسخ الأرشيف من القائمة بزر الحذف.
- أبق فقط على main والفرع النشط الحالي.
