@echo off
setlocal

rem Starts the backend bootstrap (BootstrapApp), which relaunches itself into
rem ModelServer once it has located rhapsody.jar via config.ini.
rem Usage: start.bat [path\to\config.ini]

set "SCRIPT_DIR=%~dp0"
set "OUT_DIR=%SCRIPT_DIR%out"
set "FALLBACK_JAVA=C:\Program Files\Java\jdk-26.0.1\bin\java.exe"

if not exist "%OUT_DIR%\com\sysmlfrontend\backend\BootstrapApp.class" (
    echo ERROR: %OUT_DIR% is missing compiled classes. Compile the project first ^(see backend\CLAUDE.md^).
    exit /b 1
)

where java >nul 2>nul
if %ERRORLEVEL%==0 (
    set "JAVA_EXE=java"
) else if exist "%FALLBACK_JAVA%" (
    set "JAVA_EXE=%FALLBACK_JAVA%"
) else (
    echo ERROR: no "java" on PATH and fallback JDK not found at "%FALLBACK_JAVA%".
    exit /b 1
)

"%JAVA_EXE%" -cp "%OUT_DIR%" com.sysmlfrontend.backend.BootstrapApp %*

endlocal
