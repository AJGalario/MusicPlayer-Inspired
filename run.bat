@echo off
setlocal EnableDelayedExpansion

set MODE=%~1
if "%MODE%"=="" set MODE=desktop

:: Handle mode routing
if /i "%MODE%"=="widget" goto :launch_widget
if /i "%MODE%"=="build" (
    call "%~dp0build.bat"
    goto :eof
)

:launch_desktop
echo ============================================================
echo   HeadSet Player - Native Desktop
echo ============================================================

:: Locate Java
set JAVA_CMD=java
where java >nul 2>&1
if errorlevel 1 (
    if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set JAVA_CMD="%JAVA_HOME%\bin\java.exe"
    if exist "C:\Users\Aaron John\jdk-25.0.2\bin\java.exe" set JAVA_CMD="C:\Users\Aaron John\jdk-25.0.2\bin\java.exe"
    for /d %%D in ("C:\Program Files\Java\jdk*" "C:\Program Files\Eclipse Adoptium\jdk*" "C:\Java\jdk*") do (
        if exist "%%D\bin\java.exe" set JAVA_CMD="%%D\bin\java.exe"
    )
)

:: Locate JavaFX jars
set JFX_PATH=
if exist "%~dp0lib\javafx-win" set JFX_PATH=%~dp0lib\javafx-win
if exist "C:\HeadSet-Build\javafx-win" set JFX_PATH=C:\HeadSet-Build\javafx-win
if exist "%~dp0_dist\lib" set JFX_PATH=%~dp0_dist\lib
if exist "C:\HeadSet-Build\_dist\lib" set JFX_PATH=C:\HeadSet-Build\_dist\lib

if "%JFX_PATH%"=="" (
    echo [!] JavaFX libraries not found. Running build setup first...
    call "%~dp0build.bat"
    set JFX_PATH=C:\HeadSet-Build\javafx-win
    if not exist "!JFX_PATH!" set JFX_PATH=%~dp0lib\javafx-win
)

echo [i] Using Java: %JAVA_CMD%
echo [i] Using JavaFX: %JFX_PATH%

if exist "%~dp0dist\HeadSet\HeadSet.exe" (
    start "" "%~dp0dist\HeadSet\HeadSet.exe"
    goto :eof
)
if exist "C:\HeadSet-Build\output\HeadSet\HeadSet.exe" (
    start "" "C:\HeadSet-Build\output\HeadSet\HeadSet.exe"
    goto :eof
)

if exist "%~dp0_dist\headset.jar" (
    %JAVA_CMD% --module-path "%JFX_PATH%" --add-modules javafx.controls,javafx.media,javafx.swing -jar "%~dp0_dist\headset.jar"
) else if exist "C:\HeadSet-Build\_dist\headset.jar" (
    %JAVA_CMD% --module-path "%JFX_PATH%" --add-modules javafx.controls,javafx.media,javafx.swing -jar "C:\HeadSet-Build\_dist\headset.jar"
) else (
    %JAVA_CMD% --module-path "%JFX_PATH%" --add-modules javafx.controls,javafx.media,javafx.swing -cp "C:\HeadSet-Build\_compile;%~dp0" HeadSetApp
)
goto :eof

:launch_widget
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
