# 🗺️ خارطة التطوير التالية — بدون نقص أي ميزة

## ما تم في هذا الـ Commit (اكمل وطور وحسن)
- ✅ إصلاح `Approvals.jsx` → عقد API 51/51 أخضر
- ✅ ملء `MessageServiceTest 78 سطر` + `CertificatePinnerTest 43 سطر`
- ✅ إزالة Cache + `.gitignore` → `working tree clean`
- ✅ إنشاء `IMPROVEMENT_REPORT_20260809.md`
- ✅ **الآن:** تفكيك `RedDashboard 1787 → 5 شاشات` (scaffold) + تحسين `MediaService` (thumbnail/orphan stubs) + تعليق `SecurityConfig` لـ HttpOnly/CSRF

## ما تبقى حسب الأولوية (A حرج, B تحسين, C إضافة)

### A — حرج قبل الإنتاج
1. **نقل فعلي لمنطق RedDashboard** (الـ 5 ملفات placeholder → نقل 29 دالة)
2. **Groups E2EE Sender Keys** (توزيع وتدوير عند add/remove)
3. **اختبار TURN بين شبكتين + DINSTAR عتاد حقيقي**
4. ** thumbnails حقيقية + malware scan + backup drill**

### B — تحسين
5. نقل الـ 9 صور LFS خارج LFS (حجمها صغير)
6. `npm ci` + `tsc` في CI
7. ملء `SocialUuidV7Test` + اختبار Group بهاتفين

### C — إضافات
8. بحث محلي FTS5 مشفر + رسائل مؤقتة + @/#

> كل خطوة تُنفذ في Commit منفصل مع اختبار `check-all.sh` أخضر.
