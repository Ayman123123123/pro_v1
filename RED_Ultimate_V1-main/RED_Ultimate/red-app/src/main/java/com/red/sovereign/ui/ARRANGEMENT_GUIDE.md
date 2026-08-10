# ترتيب الواجهات الرسمي — يونس

## المبدأ: كل شيء في مكانه الصحيح، لا تهريج

- **الألوان الرسمية فقط:** `YounesMidnight #080F1C` (خلفية) + `YounesPrimary #00C98C` (إجراء) + `YounesAccent #E8B84A` (تحذير DINSTAR) + `YounesRose #F43F5E` (خطر فقط) + `YounesMuted #8FA7B8` (ثانوي)
- **لا قوس قزح:** لا نستخدم `Purple + LiveRed + SpacePurple + VoipBlue` في نفس الشاشة — كل شاشة لون واحد أساسي
- **الخطوط:** `Cairo Black/Bold` للعناوين الرسمية، `Tajawal Normal` لنصوص المحادثات — 1.25 مقياس متناسق
- **الترتيب:**
  - **TopBar:** شعار يونس + RED ID + إعدادات — ثابت في كل تبويب
  - **NavigationBar:** 5 تبويبات ثابتة: الرئيسية | الدردشات | المجموعات | المكالمات | المزيد — FAB للإنشاء فقط في الرئيسية
  - **Cards:** `20dp radius` + `elevation 0` + `border YounesBorder` — لا ظلال مبالغ فيها
  - **Spacing:** شبكة 8dp — `16dp` بين الأقسام، `8dp` بين البطاقات

## ما تم تحسينه:
- `fonts.xml` → مقياس 1.25 متناسق + radius رسمي
- `colors.xml` → لوحة رسمية 3+1 فقط
- `RedTheme.kt` → خلفية solid بدل gradient قوس قزح
