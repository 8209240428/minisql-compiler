@echo off
rem ============================================================
rem  MiniSQL Compiler - one-click GUI launcher (Windows)
rem  Usage: double-click this file, or run: start-gui.bat
rem  Steps: locate JDK 17+ -> compile (via Maven Wrapper) -> open Swing UI
rem ============================================================
setlocal EnableExtensions
cd /d "%~dp0"

rem ---------- 1) locate a JDK 17+ ----------
set "JDK="

rem 1a) IntelliJ-style JDKs under %USERPROFILE%\.jdks (name implies 17+)
if not defined JDK for /d %%J in (
    "%USERPROFILE%\.jdks\openjdk-2*"
    "%USERPROFILE%\.jdks\ms-2*"
    "%USERPROFILE%\.jdks\ms-1*"
) do if not defined JDK if exist "%%~fJ\bin\java.exe" set "JDK=%%~fJ"

rem 1b) explicit JAVA_HOME
if not defined JDK if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JDK=%JAVA_HOME%"

rem 1c) java on PATH (fallback; must be JDK 17+ or compile below will say so)
if not defined JDK (
    for /f "delims=" %%p in ('where java 2^>nul') do if not defined JDK (
        for %%i in ("%%p") do set "JDK=%%~dpi.."
        if not exist "%JDK%\bin\java.exe" set "JDK="
    )
)

if not defined JDK (
    echo [ERROR] No JDK 17+ found.
    echo   Install a JDK 17 or newer from Adoptium or any vendor,
    echo   then set the JAVA_HOME environment variable to its install
    echo   directory and run this file again.
    pause
    exit /b 1
)
set "JAVA_HOME=%JDK%"
echo Using JDK: %JAVA_HOME%

rem ---------- 2) compile via Maven Wrapper (first run downloads Maven 3.9.9) ----------
echo Building... first run may take a moment
call "%~dp0mvnw.cmd" -q compile
if errorlevel 1 (
    echo [ERROR] Build failed. Run  mvnw.cmd test  to see the reason,
    echo   and make sure the JDK above is version 17 or newer.
    pause
    exit /b 1
)

rem ---------- 3) open the Swing UI (javaw => no console window) ----------
if defined MINISQL_NO_START (
    echo [test] MINISQL_NO_START is set; GUI launch skipped.
    exit /b 0
)
start "MiniSQL Compiler" "%JAVA_HOME%\bin\javaw.exe" -cp "%~dp0target\classes" minisql.ui.SwingApp
endlocal
exit /b 0
