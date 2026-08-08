@echo off
chcp 65001 >nul
title RED Ultimate V1 - تشغيل المنظومة
cls

echo ==============================================================================
echo   🏛️  RED Ultimate V1 — تشغيل المنصة السيادية على جهازك
echo ==============================================================================
echo.

cd /d "%~dp0RED_Ultimate_V1-main\RED_Ultimate" 2>nul || cd /d "%~dp0"

echo [1] تشغيل سريع بضغطة زر (لوحة التحكم + خادم الـ API المحلي)
echo [2] تشغيل كامل بالحاويات عبر Docker Compose
echo [3] فحص الاتصال ببوابة DINSTAR UC2000-VE-8G (192.168.11.1)
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
    echo 🔍 فحص الاتصال بالبوابة 192.168.11.1...
    ping -n 3 192.168.11.1
    pause
    exit /b
)
