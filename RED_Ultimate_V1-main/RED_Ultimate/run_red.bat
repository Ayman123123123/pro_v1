@echo off
TITLE RED Sovereign - System Starter
COLOR 0A

echo ==========================================
echo    🏛️ YOUNES Sovereign Platform
echo       System Infrastructure Starter
echo ==========================================

cd /d "%~dp0"

echo [1/4] Starting Databases (Docker)...
docker-compose up -d db-postgres db-mongo cache-redis minio

echo [2/4] Waiting for databases to initialize (15s)...
timeout /t 15 /nobreak > nul

echo [3/4] Configuring Environment (JDK 21)...
set JAVA_HOME=C:\Users\hpc01\.jdks\jbr-21.0.11
set PATH=%JAVA_HOME%\bin;%PATH%

echo [4/4] Launching Backend Server...
cd backend-server
call ..\gradlew.bat bootRun --args="--spring.data.mongodb.host=localhost --spring.data.mongodb.username=red_user --spring.data.mongodb.password=redpassword --spring.data.redis.host=localhost --spring.data.redis.password=redpassword --spring.datasource.url=jdbc:postgresql://localhost:5432/red_sovereign --spring.datasource.username=admin --spring.datasource.password=redpassword --red.security.jwt-secret=this-is-a-very-secret-key-for-local-testing-purposes-only-12345 --red.admin.password=admin123 --MINIO_PASSWORD=minioadmin"

pause
