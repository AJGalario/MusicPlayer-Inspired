@echo off
setlocal EnableDelayedExpansion

echo ============================================================
echo   HeadSet Player - Quick Run
echo ============================================================

:: Locate Java
set JAVA_CMD=java
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" set JAVA_CMD="%JAVA_HOME%\bin\java.exe"
)
if exist "C:\Users\Aaron John\jdk-25.0.2\bin\java.exe" (
    set JAVA_CMD="C:\Users\Aaron John\jdk-25.0.2\bin\java.exe"
)

:: Locate JavaFX jars
set JFX_PATH=
if exist "C:\HeadSet-Build\javafx-win" set JFX_PATH=C:\HeadSet-Build\javafx-win
if exist "%~dp0lib" set JFX_PATH=%~dp0lib
if exist "%~dp0_dist\lib" set JFX_PATH=%~dp0_dist\lib
if exist "C:\HeadSet-Build\_dist\lib" set JFX_PATH=C:\HeadSet-Build\_dist\lib

if "%JFX_PATH%"=="" (
    echo [!] JavaFX libraries not found.
    echo Running build.bat first to download libraries and compile...
    call "%~dp0build.bat"
    goto :eof
)

echo [i] Using Java: %JAVA_CMD%
echo [i] Using JavaFX from: %JFX_PATH%

if exist "C:\HeadSet-Build\_dist\headset.jar" (
    %JAVA_CMD% --module-path "%JFX_PATH%" --add-modules javafx.controls,javafx.media,javafx.swing -jar "C:\HeadSet-Build\_dist\headset.jar"
) else (
    %JAVA_CMD% --module-path "%JFX_PATH%" --add-modules javafx.controls,javafx.media,javafx.swing -cp "C:\HeadSet-Build\_compile;%~dp0" HeadSetApp
)

endlocal
