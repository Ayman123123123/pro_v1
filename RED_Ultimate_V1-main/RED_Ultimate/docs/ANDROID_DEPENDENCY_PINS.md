# تثبيت إصدارات أندرويد — بلا تعارض

تاريخ التحقق: 2026-08-13.

## libsignal

| الإصدار | الحالة |
|---|---|
| **0.86.5** | أحدث إصدار منشور على Maven Central (18 تشرين الثاني 2025). يحتوي `KEMKeyPair` و`KyberPreKeyRecord`. مثبت في `local-maven` عبر `scripts/prefetch-android-crypto.ps1`. |
| 0.99.1 / 0.96.3 | **غير منشورين**. كل المرايا تعيد 404. لا تُستخدما. |
| 0.76.1 | قديم؛ قد ينقصه Kyber الذي يستخدمه التطبيق. |
| 0.100.x | إن نُشر لاحقاً يفرض SPQR — يحتاج ترحيل بروتوكول. |

المصادر: [Maven Central 0.86.5](https://central.sonatype.com/artifact/org.signal/libsignal-android/0.86.5)، [قائمة الإصدارات](https://mvnrepository.com/artifact/org.signal/libsignal-android).

## ربط الموارد (AAPT2)

`styles.xml` كان يضع صفات **غير موجودة** في ثيم المنصة:

- `android:colorHighlight`
- `android:colorHint`
- `android:buttonStyleHighlight`
- `android:textInputStyle`

هذه صفات AppCompat/Material بدون بادئة `android:`. البناء الأونلاين أو الأوفلاين يسقط بنفس الخطأ. أُزيلت من `Theme.Younes`.

## حزمة متوافقة (المنتج القانوني `:app` / red-app)

| مكوّن | الإصدار | ملاحظة |
|---|---|---|
| AGP | 9.3.0 | مع compileSdk 37 |
| Kotlin | 2.3.21 | مع KSP 2.3.11 |
| Compose BOM | 2026.06.01 | عبر `platform()` |
| AppCompat | 1.7.1 | أُضيف صراحة لـ red-app |
| Material | 1.12.0 | أُضيف صراحة لـ red-app |
| Accompanist | 0.37.3 | 0.28.0 قديم جداً أمام Compose 2026 |
| libsignal | **0.86.5** | الوحيد المنشور + Kyber |

## شبكة اليمن / Windows

1. `dl.google.com` و`maven-central.storage-download.googleapis.com` و`maven.aliyun.com` أولاً.
2. لا تعتمد `--offline` إلا بعد نجاح بناء واحد أونلاين (Google + Aliyun).
3. قبل البناء: `.\scripts\prefetch-android-crypto.ps1` ثم `.\scripts\extract-or-build-apk.ps1`.
