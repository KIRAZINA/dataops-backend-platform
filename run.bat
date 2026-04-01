@echo off
setlocal

echo.
echo ================================================
echo        DATAOPS PLATFORM - LAUNCHING MONOLITH
echo ================================================
echo.

cd /d "%~dp0"

set "JAR_PATH=dataops-platform-monolith\target\dataops-platform-monolith-0.0.1-SNAPSHOT.jar"

if not exist "%JAR_PATH%" (
    echo [INFO] JAR not found - building the project...

    if exist "mvnw.cmd" (
        call mvnw.cmd clean install -DskipTests
    ) else (
        where mvn >nul 2>nul
        if %errorlevel% neq 0 (
            echo [ERROR] Neither mvnw.cmd nor mvn was found.
            echo [ERROR] Install Maven or add the Maven Wrapper to this repository.
            pause
            exit /b 1
        )
        call mvn clean install -DskipTests
    )

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

java -Xmx2g -XX:+UseG1GC -jar "%JAR_PATH%"

echo.
echo [INFO] Server stopped.
pause
