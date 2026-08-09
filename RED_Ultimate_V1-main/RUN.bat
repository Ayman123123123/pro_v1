@echo off
chcp 65001 >nul
title YOUNES Sovereign RED Ultimate V1
cls

echo ==============================================================================
echo   🏛️  YOUNES Sovereign — RED Ultimate V1 — التشغيل الفوري على Windows
echo ==============================================================================
echo.

rem تحديد جذر المشروع (سواء شُغّل من الجذر أو من داخل مجلد فرعي)
set "DIR=%~dp0"
if exist "%DIR%RED_Ultimate\docker-compose.yml" (
    set "ROOT=%DIR%RED_Ultimate"
) else if exist "%DIR%RED_Ultimate_V1-main\RED_Ultimate\docker-compose.yml" (
    set "ROOT=%DIR%RED_Ultimate_V1-main\RED_Ultimate"
) else (
    set "ROOT=%DIR%"
)

echo   المشروع: %ROOT%
echo.
echo   [1] تشغيل كامل عبر Docker Compose (موصى به للإنتاج المحلي)
echo   [2] تشغيل سريع للتطوير (لوحة الإدارة + API محلي)
echo   [3] لوحة الإدارة فقط (Vite React على 8088)
echo   [4] فحص اتصال بوابة DINSTAR UC2000-VE-8G (192.168.11.1)
echo   [5] الفحص الشامل الآلي (11 فحصًا)
echo.
set /p CHOICE="اختر رقم الخيار [1]: "
if "%CHOICE%"=="" set CHOICE=1

if "%CHOICE%"=="1" (
    echo.
    echo 🐳 جاري تشغيل الحاويات عبر Docker Compose...
    cd /d "%ROOT%"
    if not exist .env (
        copy .env.example .env >nul
        echo ⚠️  تم إنشاء .env من القالب — عدّل الأسرار قبل الإنتاج!
    )
    docker compose up -d --build
    echo.
    echo ✅ اللوحة: http://localhost:8088
    echo ✅ الصحة: http://localhost:8080/health
    pause
    exit /b
)

if "%CHOICE%"=="2" (
    echo.
    echo ⚡ جاري تشغيل بيئة التطوير السريعة...
    cd /d "%ROOT%"
    start "RED Mock API" /min python scripts\mock_backend.py
    cd admin_dashboard
    call npm install
    set "RED_API_TARGET=http://127.0.0.1:8080"
    call npm run dev -- --port 8088
    pause
    exit /b
)

if "%CHOICE%"=="3" (
    echo.
    echo 🌐 جاري تشغيل لوحة الإدارة...
    cd /d "%ROOT%\admin_dashboard"
    call npm install
    call npm run dev -- --port 8088
    pause
    exit /b
)

if "%CHOICE%"=="4" (
    echo.
    echo 🔍 فحص الاتصال ببوابة DINSTAR...
    ping -n 2 192.168.11.1
    echo.
    echo 💡 تأكد من ضبط عنوان IP كرت الشبكة المتصل بالبوابة إلى: 192.168.11.22
    pause
    exit /b
)

if "%CHOICE%"=="5" (
    echo.
    echo 🔍 جاري تشغيل الفحص الشامل الآلي (11 فحصًا)...
    cd /d "%ROOT%"
    bash scripts\check-all.sh
    pause
    exit /b
)

echo ❌ خيار غير معروف.
pause
