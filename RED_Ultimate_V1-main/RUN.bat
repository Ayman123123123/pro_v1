@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul
title YOUNES Sovereign RED Ultimate V1
cls

echo ==============================================================================
echo   YOUNES Sovereign - RED Ultimate V1 - المشغل الموحد لنظام Windows
echo ==============================================================================
echo.

rem اكتشاف مجلد المشروع الرئيسي (يقبل المواقع الممكنة)
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
echo   [1] تشغيل المنصة الكاملة Docker Compose (يفضل لتشغيل كامل المنظومة)
echo   [2] تشغيل خادم التطوير (الخادم الوهمي + API المحلي)
echo   [3] لوحة التحكم وحدها (Vite React على 8088)
echo   [4] فحص بوابات DINSTAR UC2000-VE-8G (192.168.11.1)
echo   [5] تشغيل الفحوصات الشاملة (11 فحصاً)
echo.
set /p CHOICE="اختر خيار التشغيل [1]: "
if "%CHOICE%"=="" set CHOICE=1

if "%CHOICE%"=="1" (
    echo.
    echo جاري تشغيل المنصة: تجهيز البيئة + رفع Compose + متابعة التشغيل...
    cd /d "%ROOT%"
    where bash >nul 2>&1 || (
        echo لا يوجد Git Bash لتشغيل سكربت تشغيل البيئة scripts/local-first-run.sh
        pause
        exit /b 1
    )
    for /f %%i in ('powershell -NoProfile -Command "(Get-NetIPAddress -AddressFamily IPv4 ^| Where-Object {$_.IPAddress -notlike '127.*' -and $_.PrefixOrigin -ne 'WellKnown'} ^| Select-Object -First 1 -ExpandProperty IPAddress)"') do set "SERVER_IP=%%i"
    if "!SERVER_IP!"=="" set "SERVER_IP=127.0.0.1"
    bash scripts/local-first-run.sh --server-ip "!SERVER_IP!"
    if errorlevel 1 (
        echo فشل التشغيل. راجع سجل التشغيل.
        pause
        exit /b 1
    )
    pause
    exit /b
)

if "%CHOICE%"=="2" (
    echo.
    echo جاري تشغيل خادم التطوير المحلي...
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
    echo جاري تشغيل لوحة التحكم...
    cd /d "%ROOT%\admin_dashboard"
    call npm install
    call npm run dev -- --port 8088
    pause
    exit /b
)

if "%CHOICE%"=="4" (
    echo.
    echo فحص بوابات DINSTAR...
    ping -n 2 192.168.11.1
    echo.
    echo يمكن تغيير عنوان IP البوابة من إعدادات الشبكة على: 192.168.11.22
    pause
    exit /b
)

if "%CHOICE%"=="5" (
    echo.
    echo جاري تشغيل الفحوصات الشاملة (11 فحصاً)...
    cd /d "%ROOT%"
    bash scripts\check-all.sh
    pause
    exit /b
)

echo خيار غير صالح.
pause
