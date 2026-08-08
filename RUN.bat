@echo off
chcp 65001 >nul
title RED Ultimate V1 - تشغيل المنظومة السيادية
cls

echo ==============================================================================
echo   🏛️  RED Ultimate V1 — تشغيل المنصة السيادية على جهازك
echo ==============================================================================
echo.

cd /d "%~dp0RED_Ultimate_V1-main\RED_Ultimate" 2>nul || cd /d "%~dp0"

echo [1] تشغيل فوري وسريع (لوحة التحكم + الـ API المحلي - بدون Docker) [الأسرع]
echo [2] بناء وتشغيل كامل بالحاويات عبر Docker Compose
echo [3] بناء JAR محلياً أولاً ثم تشغيل Docker (حل مشاكل DNS في Docker Desktop)
echo [4] فحص الاتصال ببوابة DINSTAR UC2000-VE-8G (192.168.11.1)
echo.
set /p OPT="اختر طريقة التشغيل [1]: "
if "%OPT%"=="" set OPT=1

if "%OPT%"=="1" (
    echo.
    echo ⚡ جاري تشغيل خادم الـ API المحلي...
    start /b python scripts\mock_backend.py 2>nul || start /b python3 scripts\mock_backend.py 2>nul
    echo 🌐 جاري تشغيل لوحة التحكم...
    cd admin_dashboard
    call npm install
    call npm run dev
    pause
    exit /b
)

if "%OPT%"=="2" (
    echo.
    echo 🐳 جاري تشغيل Docker Compose...
    if not exist .env copy .env.example .env >nul
    docker compose up -d --build
    echo.
    echo ✅ تم التشغيل! افتح المتصفح على: http://localhost:8088
    pause
    exit /b
)

if "%OPT%"=="3" (
    echo.
    echo ⚙️ جاري بناء ملف الـ JAR محلياً عبر Gradle (يتجاوز مشاكل DNS الحاويات)...
    call ..\gradlew.bat :backend-server:bootJar --no-daemon -x test 2>nul || call gradlew.bat :backend-server:bootJar --no-daemon -x test
    echo.
    echo 🐳 جاري تشغيل Docker باستخدام الـ JAR المبني...
    docker compose build --build-arg JAR_FILE=backend-server/build/libs/*.jar backend
    docker compose up -d
    echo.
    echo ✅ تم التشغيل بنجاح! اللوحة على: http://localhost:8088
    pause
    exit /b
)

if "%OPT%"=="4" (
    echo.
    echo 🔍 فحص الاتصال بالبوابة 192.168.11.1...
    ping -n 3 192.168.11.1
    pause
    exit /b
)
