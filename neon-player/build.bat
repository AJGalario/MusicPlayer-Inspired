@echo off
setlocal EnableDelayedExpansion
echo.
echo ============================================
echo   HeadSet - Native Desktop App Builder
echo ============================================
echo.

if "%JDK%"=="" if exist "C:\Users\Aaron John\jdk-25.0.2" set JDK=C:\Users\Aaron John\jdk-25.0.2
if "%JDK%"=="" if defined JAVA_HOME set JDK=%JAVA_HOME%
if "%JDK%"=="" (
    for /f "tokens=*" %%i in ('where javac.exe 2^>nul') do (
        set JAVAC_TMP=%%i
        set JDK=!JAVAC_TMP:\bin\javac.exe=!
    )
)
set JAVAC=%JDK%\bin\javac.exe
set JAR=%JDK%\bin\jar.exe
set JPACKAGE=%JDK%\bin\jpackage.exe
set SRC=C:\Users\Aaron John\Downloads\neon-player\neon-player
set BUILD=C:\HeadSet-Build\_compile
set DIST=C:\HeadSet-Build\_dist
set OUT=C:\HeadSet-Build\output
set JFX=C:\HeadSet-Build\javafx-win
set MAVEN=https://repo1.maven.org/maven2/org/openjfx

if not exist "%JAVAC%"    ( echo ERROR: javac not found & exit /b 1 )
if not exist "%JPACKAGE%" ( echo ERROR: jpackage not found & exit /b 1 )

:: ---- Step 1: Clean -------------------------------------------------------
echo [1/8] Cleaning...
if exist C:\HeadSet-Build rmdir /s /q C:\HeadSet-Build
mkdir "%BUILD%"
mkdir "%DIST%"
mkdir "%OUT%"
mkdir "%JFX%"
echo     OK

:: ---- Step 2: Download JavaFX 21 WIN JARs (contain classes + module-info) -
echo [2/8] Downloading JavaFX 21 WIN JARs from Maven Central...
echo     (The -win.jar files contain the actual classes and native DLLs)
set MODS=javafx-base javafx-controls javafx-fxml javafx-graphics javafx-swing javafx-web javafx-media

for %%M in (%MODS%) do (
    echo     ^>^> %%M-win...
    call :download "%MAVEN%/%%M/21/%%M-21-win.jar" "%JFX%\%%M-21-win.jar" "%%M-21-win.jar"
)

set COUNT=0
for %%F in ("%JFX%\*.jar") do set /a COUNT+=1
echo     OK: %COUNT% JARs downloaded

:: ---- Step 3: Compile with the win JARs as module-path -------------------
echo [3/8] Compiling Java sources...

:: Build a semicolon-separated classpath for the -cp flag (fallback)
set JFX_CP=%JFX%\javafx-base-21-win.jar;%JFX%\javafx-controls-21-win.jar;%JFX%\javafx-fxml-21-win.jar;%JFX%\javafx-graphics-21-win.jar;%JFX%\javafx-swing-21-win.jar;%JFX%\javafx-web-21-win.jar;%JFX%\javafx-media-21-win.jar

"%JAVAC%" ^
    --release 17 ^
    --module-path "%JFX%" ^
    --add-modules javafx.controls,javafx.media,javafx.swing ^
    -d "%BUILD%" ^
    "%SRC%\HeadSetApp.java" "%SRC%\MusicServer.java"

if errorlevel 1 (
    echo     Module-path failed, trying classpath mode...
    "%JAVAC%" ^
        --release 17 ^
        -cp "%JFX_CP%" ^
        --add-modules ALL-SYSTEM ^
        -d "%BUILD%" ^
        "%SRC%\HeadSetApp.java" "%SRC%\MusicServer.java"
    if errorlevel 1 (
        echo ERROR: Compilation failed
        exit /b 1
    )
)
echo     OK: Compiled

:: ---- Step 4: Create JAR --------------------------------------------------
echo [4/8] Creating headset.jar...
(echo Main-Class: HeadSetApp & echo.) > "%BUILD%\MANIFEST.MF"
pushd "%BUILD%"
"%JAR%" cfm "%DIST%\headset.jar" "%BUILD%\MANIFEST.MF" .
set JERR=%ERRORLEVEL%
popd
if %JERR% neq 0 ( echo ERROR: JAR failed & exit /b 1 )
echo     OK: headset.jar

:: ---- Step 5: Copy resources ----------------------------------------------
echo [5/8] Copying resources...
xcopy /E /I /Q /Y "%SRC%\public" "%DIST%\public"  >nul
if exist "%SRC%\music" (
    xcopy /E /I /Q /Y "%SRC%\music" "%DIST%\music" >nul
) else (
    mkdir "%DIST%\music"
)
:: Bundle JavaFX win JARs in dist\lib
mkdir "%DIST%\lib"
copy /Y "%JFX%\*.jar" "%DIST%\lib\" >nul
echo     OK

:: ---- Step 6: Icon --------------------------------------------------------
echo [6/8] Generating icon...
set ICON=%DIST%\icon.ico
powershell -NoProfile -Command "Add-Type -AssemblyName System.Drawing; $b=New-Object System.Drawing.Bitmap(256,256); $g=[System.Drawing.Graphics]::FromImage($b); $g.SmoothingMode='AntiAlias'; $g.FillEllipse((New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255,124,111,205))),0,0,255,255); $p=New-Object System.Drawing.Pen([System.Drawing.Color]::White,20); $p.StartCap='Round'; $p.EndCap='Round'; $g.DrawArc($p,50,44,156,110,180,180); $e=New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White); $g.FillRectangle($e,30,130,40,56); $g.FillEllipse($e,26,118,48,28); $g.FillEllipse($e,26,158,48,28); $g.FillRectangle($e,186,130,40,56); $g.FillEllipse($e,182,118,48,28); $g.FillEllipse($e,182,158,48,28); $g.Dispose(); $ic=[System.Drawing.Icon]::FromHandle($b.GetHicon()); $fs=[System.IO.File]::OpenWrite('%ICON%'); $ic.Save($fs); $fs.Close(); $b.Dispose()" 2>nul
if exist "%ICON%" ( echo     OK: Icon ) else ( echo     Skipped )

:: ---- Step 7: jpackage ----------------------------------------------------
echo [7/8] Building EXE with jpackage (1-2 min)...
set JP="%JPACKAGE%" --type app-image --name HeadSet --input "%DIST%" --main-jar headset.jar --main-class HeadSetApp --dest "%OUT%" --app-version 1.0.0 --vendor HeadSet --java-options -Xmx256m --java-options -Djava.awt.headless=false --java-options "--module-path lib" --java-options "--add-modules javafx.controls,javafx.media,javafx.swing" --java-options "--add-opens javafx.graphics/com.sun.javafx.sg.prism=ALL-UNNAMED"

if exist "%ICON%" (
    %JP% --icon "%ICON%"
) else (
    %JP%
)
if errorlevel 1 ( echo ERROR: jpackage failed & exit /b 1 )
echo     OK: EXE built

:: ---- Step 8: Verify ------------------------------------------------------
echo [8/8] Verifying...
if not exist "%OUT%\HeadSet\HeadSet.exe" ( echo ERROR: EXE missing & exit /b 1 )

echo.
echo ============================================
echo   BUILD SUCCESSFUL!
echo ============================================
echo.
echo   EXE : %OUT%\HeadSet\HeadSet.exe
echo.
echo   Double-click HeadSet.exe
echo   Opens as its OWN native window - no browser!
echo   Right-click tray icon to Quit.
echo.
start explorer.exe "%OUT%\HeadSet"
endlocal
goto :eof

:: ---- :download (validate zip, retry 3x) ---------------------------------
:download
set _URL=%~1
set _DST=%~2
set _NM=%~3
for /L %%R in (1,1,3) do (
    if exist "%_DST%" del /f /q "%_DST%"
    curl.exe -s -L --fail --max-time 90 --retry 2 -o "%_DST%" "%_URL%" 2>nul
    if exist "%_DST%" (
        "%JAR%" tf "%_DST%" >nul 2>&1
        if !ERRORLEVEL! equ 0 ( echo         %_NM% OK & goto :eof )
        echo         %_NM% corrupted, retrying...
        del /f /q "%_DST%" 2>nul
    ) else (
        echo         %_NM% failed, retrying...
    )
)
echo         WARNING: %_NM% unavailable
goto :eof
