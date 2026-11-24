@echo off
setlocal EnableDelayedExpansion

set MODE=%~1
if "%MODE%"=="" set MODE=widget

if /i "%MODE%"=="build" (
    call "%~dp0build.bat"
    goto :eof
)

echo ============================================================
echo   HeadSet - Compact PC Widget
echo ============================================================
set "HEADSET_WIDGET=1"

:: Locate Java
set JAVABIN=java
where java >nul 2>&1
if errorlevel 1 (
    if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set JAVABIN="%JAVA_HOME%\bin\java.exe"
    if exist "C:\Users\Aaron John\jdk-25.0.2\bin\java.exe" set JAVABIN="C:\Users\Aaron John\jdk-25.0.2\bin\java.exe"
    for /d %%D in ("C:\Program Files\Java\jdk*" "C:\Program Files\Eclipse Adoptium\jdk*" "C:\Java\jdk*") do (
        if exist "%%D\bin\java.exe" set JAVABIN="%%D\bin\java.exe"
    )
)

if exist "%~dp0HeadSet-Widget-App\MusicServer.java" (
    cd /d "%~dp0HeadSet-Widget-App"
    %JAVABIN% MusicServer.java
) else if exist "%~dp0MusicServer.java" (
    cd /d "%~dp0"
    %JAVABIN% MusicServer.java
)

endlocal
