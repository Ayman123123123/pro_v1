# 🔐 تشفير المجموعات E2EE — Sender Keys

## الحالة بعد Commit a965efd

`GroupCryptoManager.kt` كان موجوداً لكن غير موثق و `GroupService` لم يكن ينبه العملاء لتدوير المفتاح.

## ما تم تطويره الآن

### 1. GroupCryptoManager (red-app)
- `prepare(group, plaintext)` يحسب `membershipHash = SHA-256(sorted redId:userId:role)` 
- إذا `distributionId == null` أو `membershipHash تغير` → يولد `UUID` جديد + `GroupSessionBuilder.create()` → يوزع `SenderKeyDistributionMessage` مشفر pairwise (type 2/3) لكل عضو
- `GroupCipher.encrypt(distributionId, plaintext)` ينتج type 4
- `processDistribution` يستقبل ويعالج `SenderKeyDistributionMessage`
- `decrypt` يفك عبر `GroupCipher.decrypt`
- `rotate(groupId)` يحذف `distribution + membership` من SecureStore

### 2. GroupService (backend)
- `add` / `remove` / `leave` الآن تعلق `// 🔐 E2EE: Membership changed — clients must rotate`
- `touch(groupId)` يحدث `updatedAt` — إشارة للعملاء أن العضوية تغيرت

### 3. MessageService (backend)
- أُضيف تحقق صارم: `if (type in GROUP_TYPES) require(conversationId 8..128)`
- `enforceGroupMembership` يتحقق أن `sender` و `receiver` عضوان في `groupId == conversationId`

### 4. اختبارات
- `GroupE2EETest.kt` — 6 اختبارات: hash change on add/role, ciphertext type 4, distribution via pairwise, conversationId, member check

## ما تبقى (يحتاج هاتفين)
- اختبار `3 هواتف + add/remove` فعلي
- `Sender Keys` لا تدعم `remove` بلا إعادة توزيع كاملة — هذا هو التصميم الحالي (يعيد distribution كاملة)
