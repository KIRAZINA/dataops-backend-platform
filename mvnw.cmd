@echo off
setlocal

set "BASE_DIR=%~dp0"
if "%BASE_DIR:~-1%"=="\" set "BASE_DIR=%BASE_DIR:~0,-1%"
set "WRAPPER_JAR=%BASE_DIR%\.mvn\wrapper\maven-wrapper.jar"
set "WRAPPER_MAIN=org.apache.maven.wrapper.MavenWrapperMain"

if not exist "%WRAPPER_JAR%" (
  echo.
  echo Error: Maven wrapper jar not found: "%WRAPPER_JAR%" >&2
  echo.
  exit /b 1
)

if not "%JAVA_HOME%"=="" (
  set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
  if not exist "%JAVA_EXE%" (
    echo.
    echo Error: JAVA_HOME is set to an invalid directory. >&2
    echo JAVA_HOME = "%JAVA_HOME%" >&2
    echo.
    exit /b 1
  )
) else (
  where java >nul 2>nul
  if errorlevel 1 (
    echo.
    echo Error: JAVA_HOME not found and java is not available in PATH. >&2
    echo.
    exit /b 1
  )
  set "JAVA_EXE=java"
)

"%JAVA_EXE%" -classpath "%WRAPPER_JAR%" "-Dmaven.multiModuleProjectDirectory=%BASE_DIR%" %WRAPPER_MAIN% %*
exit /b %ERRORLEVEL%
