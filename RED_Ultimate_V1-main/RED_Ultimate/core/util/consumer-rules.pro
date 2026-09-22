# Consumer ProGuard rules for core:util module
-keepclassmembers class * {
    public static ** valueOf(java.lang.String);
    public static **[] values();
}
