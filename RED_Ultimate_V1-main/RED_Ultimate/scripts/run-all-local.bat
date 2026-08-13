@echo off
chcp 65001 >nul
title YOUNES — تشغيل المنصة الحقيقية
cls
echo YOUNES — Docker Compose فقط (Kotlin + Postgres + Mongo + Redis + Nginx)
echo لا يوجد مسار Node/SQLite. اللوحة على المنفذ 8088.
echo.
set SCRIPT_DIR=%~dp0
set ROOT_DIR=%SCRIPT_DIR%..
cd /d "%ROOT_DIR%"
powershell -ExecutionPolicy Bypass -File "%ROOT_DIR%\scripts\compose-recover.ps1" -RebuildBackend
echo.
echo لوحة الإدارة: http://127.0.0.1:8088/
echo كلمة المسؤول من ملف .env : RED_ADMIN_USERNAME / RED_ADMIN_PASSWORD
pause
