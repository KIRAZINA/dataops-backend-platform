@echo off
echo.
echo ╔══════════════════════════════════════════════════════╗
echo ║        DATAOPS PLATFORM — LAUNCHING MONOLITH         ║
echo ╚══════════════════════════════════════════════════════╝
echo.

REM Ensure we are in the project root
cd /d "%~dp0"

REM Check if the fat JAR exists
if not exist "dataops-platform-monolith\target\dataops-platform-monolith-0.0.1-SNAPSHOT.jar" (
    echo [INFO] JAR not found — building the project...
    call mvnw clean install -DskipTests
    if %errorlevel% neq 0 (
        echo [ERROR] Build failed!
        pause
        exit /b %errorlevel%
    )
    echo [INFO] Build completed successfully.
    echo.
)

echo [INFO] Starting DataOps Platform Monolith...
echo [INFO] Swagger UI : http://localhost:8080/swagger-ui.html
echo [INFO] Actuator   : http://localhost:8080/actuator
echo.

java -Xmx2g -XX:+UseG1GC -jar dataops-platform-monolith\target\dataops-platform-monolith-0.0.1-SNAPSHOT.jar

echo.
echo [INFO] Server stopped.
pause