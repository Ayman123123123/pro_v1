# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
# قواعد ProGuard/R8 لتطبيق يونس السيادي
# بدون هذه القواعد، isMinifyEnabled=true يُحطّم التطبيق عند أول استعمال حقيقي
# (لا يظهر الخطأ في التصريف بل في الـ runtime).
# ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

# ─── البروتوكول المشترك (Protobuf) ────────────────────────────────
-keep class com.red.sovereign.proto.** { *; }

# ─── libsignal (PQXDH + Double Ratchet + Kyber) ────────────────────
-keep class org.signal.libsignal.** { *; }
-dontwarn org.signal.libsignal.**

# ─── kotlinx.serialization — 82 صنفًا @Serializable، انهيار JSON بدون هذا ──
# القاعدة الرسمية تحفظ كائنات الرفيق المولّدة وحقول الـ @Serializable
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **.*$Companion {
    *;
}
-keepclasseswithmembers class com.red.sovereign.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# احفظ كل الأصناف المشروحة بـ @Serializable ومتغيراتها
-keep @kotlinx.serialization.Serializable class com.red.sovereign.** { *; }
-keepclassmembers @kotlinx.serialization.Serializable class com.red.sovereign.** {
    static <1>$Companion Companion;
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
    ***[] $VALUES;
    *;
}

# ─── Room (كيانات + DAO) ────────────────────────────────────────────
-keep class com.red.sovereign.core.database.*Entity { *; }
-keep class com.red.sovereign.core.database.RedDao { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# ─── WebRTC (JNI + native) — تعطّل المكالمات بدونه ──────────────────
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# ─── OkHttp + CertificatePinner ─────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keep class com.red.sovereign.security.CertificatePinner { *; }

# ─── Compose / Material3 — يُدار بواسطة Compose compiler عادةً ──────
-dontwarn androidx.compose.**

# ─── Coil (تحميل الصور) ─────────────────────────────────────────────
-dontwarn coil.**

# ─── Lottie ─────────────────────────────────────────────────────────
-dontwarn com.airbnb.lottie.**

# ─── Kotlin Metadata — ضروري للانعكاس ───────────────────────────────
-keepattributes Signature, Exceptions, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# ─── SQLCipher ──────────────────────────────────────────────────────
-keep class net.zetetic.database.** { *; }
-dontwarn net.zetetic.database.**

# ─── Google Fonts / emoji2 ──────────────────────────────────────────
-dontwarn androidx.compose.ui.text.google.**
-dontwarn androidx.text.**

# ─── خرائط الجذر الآمنة ─────────────────────────────────────────────
-keep class com.red.sovereign.MainActivity { *; }
-keep class com.red.sovereign.YounesApplication { *; }
