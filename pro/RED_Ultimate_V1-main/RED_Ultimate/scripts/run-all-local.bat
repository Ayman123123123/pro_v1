@echo off
chcp 65001 >nul
title RED Ultimate V1 - تشغيل المنصة محلياً
cls

echo ==============================================================================
echo   🏛️  RED Ultimate V1 — تشغيل المنصة السيادية محلياً على Windows
echo ==============================================================================
echo.

set SCRIPT_DIR=%~dp0
set ROOT_DIR=%SCRIPT_DIR%..

echo [1] تشغيل كامل عبر Docker Compose (الخيار الموصى به)
echo [2] تشغيل سريع للتطوير (لوحة الإدارة + الـ API المحلي)
echo [3] تشغيل لوحة الإدارة فقط (Vite React)
echo [4] فحص الاتصال ببوابة DINSTAR UC2000-VE-8G (192.168.11.1)
echo.
set /p CHOICE="اختر رقم الخيار [1]: "
if "%CHOICE%"=="" set CHOICE=1

if "%CHOICE%"=="1" (
    echo.
    echo 🚀 جاري تشغيل الحاويات عبر Docker Compose...
    cd /d "%ROOT_DIR%"
    if not exist .env (
        copy .env.example .env >nul
    )
    docker compose up -d --build
    echo.
    echo ✅ تم تشغيل جميع الخدمات بنجاح!
    echo 🔗 لوحة الإدارة: http://localhost:8088
    echo 🔗 واجهة الباكند: http://localhost:8080/health
    pause
    exit /b
)

if "%CHOICE%"=="2" (
    echo.
    echo ⚡ جاري تشغيل بيئة التطوير السريعة...
    cd /d "%ROOT_DIR%\admin_dashboard"
    start /b python "%ROOT_DIR%\scripts\mock_backend.py"
    call npm install
    call npm run dev
    pause
    exit /b
)

if "%CHOICE%"=="3" (
    echo.
    echo 🌐 جاري تشغيل لوحة الإدارة...
    cd /d "%ROOT_DIR%\admin_dashboard"
    call npm install
    call npm run dev
    pause
    exit /b
)

if "%CHOICE%"=="4" (
    echo.
    echo 🔍 فحص الاتصال ببوابة DINSTAR...
    ping -n 2 192.168.11.1
    echo.
    echo تأكد من ضبط عنوان IP كرت الشبكة المتصل بالبوابة إلى: 192.168.11.22
    pause
    exit /b
)
