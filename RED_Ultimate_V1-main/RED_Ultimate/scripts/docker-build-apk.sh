#!/bin/bash
# ═══════════════════════════════════════════════════════════════════
# RED Ultimate APK Builder — Docker Entry Script
# يُستدعى تلقائياً عند docker run
# ═══════════════════════════════════════════════════════════════════
set -e

echo ""
echo "╔══════════════════════════════════════════════════════════╗"
echo "║   🔨 RED Ultimate APK Builder                          ║"
echo "║   يونس ماستر — الإدارة السيادية                        ║"
echo "╚══════════════════════════════════════════════════════════╝"
echo ""

cd /build

echo "📋 Gradle Version:"
./gradlew --version 2>&1 | head -5 || echo "Gradle version check failed"
echo ""

echo "🔧 بدء بناء :app (red-app)..."
echo "   هذا قد يستغرق 10-30 دقيقة..."
echo ""

# بناء APK
./gradlew :app:assembleDebug \
    --no-daemon \
    --no-configuration-cache \
    --stacktrace \
    2>&1 | tee /build/build-output.log

BUILD_STATUS=$?

echo ""
if [ $BUILD_STATUS -eq 0 ]; then
    echo "╔══════════════════════════════════════════════════════════╗"
    echo "║   ✅ BUILD SUCCESSFUL!                                 ║"
    echo "╚══════════════════════════════════════════════════════════╝"
    echo ""
    
    # استخراج APK
    echo "📥 استخراج APK..."
    mkdir -p /output
    
    # البحث في كل المسارات المحتملة
    find /build -path "*/outputs/apk/*/*.apk" -exec cp {} /output/ \; 2>/dev/null || true
    
    echo ""
    echo "📁 APK files:"
    ls -lah /output/*.apk 2>/dev/null || echo "   (لم يتم العثور على APK)"
    echo ""
    
    # نسخ سجل البناء
    cp /build/build-output.log /output/ 2>/dev/null || true
    
else
    echo "╔══════════════════════════════════════════════════════════╗"
    echo "║   ❌ BUILD FAILED (exit code: $BUILD_STATUS)                  ║"
    echo "╚══════════════════════════════════════════════════════════╝"
    echo ""
    
    echo "📋 آخر 100 سطر من سجل البناء:"
    echo "──────────────────────────────────────────────────────────"
    tail -100 /build/build-output.log
    echo "──────────────────────────────────────────────────────────"
    echo ""
    
    # حفظ السجل
    mkdir -p /output
    cp /build/build-output.log /output/ 2>/dev/null || true
fi

echo ""
echo "📋 سجل البناء محفوظ في: /output/build-output.log"
echo ""
echo "💡 لاستخراج APK:"
echo "   docker cp <container>:/output/app-debug.apk ./"
