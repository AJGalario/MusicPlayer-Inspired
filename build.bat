@echo off
setlocal EnableDelayedExpansion
echo.
echo ============================================
echo   HeadSet - Unified App and Builder
echo ============================================
echo.

set SRC=%~dp0
if "%SRC:~-1%"=="\" set SRC=%SRC:~0,-1%

:: Detect JDK
if "%JDK%"=="" if exist "C:\Users\Aaron John\jdk-25.0.2" set JDK=C:\Users\Aaron John\jdk-25.0.2
if "%JDK%"=="" if defined JAVA_HOME set JDK=%JAVA_HOME%
if "%JDK%"=="" (
    for /f "tokens=*" %%i in ('where javac.exe 2^>nul') do (
        set JAVAC_TMP=%%i
        set JDK=!JAVAC_TMP:\bin\javac.exe=!
    )
)
if "%JDK%"=="" (
    for /d %%D in ("C:\Program Files\Java\jdk*" "C:\Program Files\Eclipse Adoptium\jdk*" "C:\Java\jdk*") do (
        if exist "%%D\bin\javac.exe" set JDK=%%D
    )
)

set JAVAC=%JDK%\bin\javac.exe
set JAR=%JDK%\bin\jar.exe
set JPACKAGE=%JDK%\bin\jpackage.exe
set BUILD=C:\HeadSet-Build\_compile
set DIST=C:\HeadSet-Build\_dist
set OUT=%SRC%\dist
set JFX=C:\HeadSet-Build\javafx-win
set MAVEN=https://repo1.maven.org/maven2/org/openjfx

if not exist "%JAVAC%"    ( echo ERROR: javac not found & exit /b 1 )
if not exist "%JPACKAGE%" ( echo ERROR: jpackage not found & exit /b 1 )

:: ---- Step 1: Clean -------------------------------------------------------
echo [1/9] Preparing build directories...
if not exist "%BUILD%" mkdir "%BUILD%"
if not exist "%DIST%" mkdir "%DIST%"
if not exist "%OUT%" mkdir "%OUT%"
if not exist "%JFX%" mkdir "%JFX%"
echo     OK

:: ---- Step 2: Download JavaFX 21 WIN JARs (if missing) ---------------------
echo [2/9] Checking JavaFX 21 WIN JARs...
set MODS=javafx-base javafx-controls javafx-fxml javafx-graphics javafx-swing javafx-web javafx-media

for %%M in (%MODS%) do (
    if not exist "%JFX%\%%M-21-win.jar" (
        echo     ^>^> Downloading %%M-win...
        call :download "%MAVEN%/%%M/21/%%M-21-win.jar" "%JFX%\%%M-21-win.jar" "%%M-21-win.jar"
    )
)

set COUNT=0
for %%F in ("%JFX%\*.jar") do set /a COUNT+=1
echo     OK: %COUNT% JavaFX JARs verified

:: ---- Step 3: Compile Java Sources ----------------------------------------
echo [3/9] Compiling Java sources (Release 17 compatibility)...
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
echo [4/9] Creating headset.jar...
(echo Main-Class: HeadSetApp & echo.) > "%BUILD%\MANIFEST.MF"
pushd "%BUILD%"
"%JAR%" cfm "%DIST%\headset.jar" "%BUILD%\MANIFEST.MF" .
set JERR=%ERRORLEVEL%
popd
if %JERR% neq 0 ( echo ERROR: JAR failed & exit /b 1 )
echo     OK: headset.jar

:: ---- Step 5: Copy resources ----------------------------------------------
echo [5/9] Copying resources...
if exist "%SRC%\public" xcopy /E /I /Q /Y "%SRC%\public" "%DIST%\public" >nul
if exist "%SRC%\music" (
    xcopy /E /I /Q /Y "%SRC%\music" "%DIST%\music" >nul
) else (
    mkdir "%DIST%\music" 2>nul
)
mkdir "%DIST%\lib" 2>nul
copy /Y "%JFX%\*.jar" "%DIST%\lib\" >nul
echo     OK

:: ---- Step 6: Generate Icon -----------------------------------------------
echo [6/9] Generating icon...
set ICON=%SRC%\icon.ico
if not exist "%ICON%" (
    powershell -NoProfile -Command "Add-Type -AssemblyName System.Drawing; $b=New-Object System.Drawing.Bitmap(256,256); $g=[System.Drawing.Graphics]::FromImage($b); $g.SmoothingMode='AntiAlias'; $br=New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255,142,117,255)); $g.FillEllipse($br,0,0,255,255); $p=New-Object System.Drawing.Pen([System.Drawing.Color]::White,20); $p.StartCap='Round'; $p.EndCap='Round'; $g.DrawArc($p,50,44,156,110,180,180); $e=New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White); $g.FillRectangle($e,30,130,40,56); $g.FillEllipse($e,26,118,48,28); $g.FillEllipse($e,26,158,48,28); $g.FillRectangle($e,186,130,40,56); $g.FillEllipse($e,182,118,48,28); $g.FillEllipse($e,182,158,48,28); $g.Dispose(); $ic=[System.Drawing.Icon]::FromHandle($b.GetHicon()); $fs=[System.IO.File]::OpenWrite('%ICON%'); $ic.Save($fs); $fs.Close(); $b.Dispose()" 2>nul
)
if exist "%ICON%" copy /Y "%ICON%" "%DIST%\icon.ico" >nul
echo     OK: Icon

:: ---- Step 7: Compile Root HeadSet.exe Launcher ---------------------------
echo [7/9] Compiling root HeadSet.exe launcher...
if exist "C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe" (
    "C:\Windows\Microsoft.NET\Framework64\v4.0.30319\csc.exe" /nologo /target:winexe /out:"%SRC%\HeadSet.exe" /r:System.Windows.Forms.dll /r:System.Drawing.dll /optimize+ "%SRC%\Launcher.cs" 2>nul
    echo     OK: Root HeadSet.exe compiled
) else (
    echo     Skipped csc (Framework 4.0 not in default location)
)

:: ---- Step 8: Build Standalone Windows App (jpackage) ---------------------
echo [8/9] Building standalone Windows bundle with jpackage...
set JP="%JPACKAGE%" --type app-image --name HeadSet --input "%DIST%" --main-jar headset.jar --main-class HeadSetApp --dest "%OUT%" --app-version 1.0.0 --vendor HeadSet --java-options -Xmx256m --java-options -Djava.awt.headless=false --java-options "--module-path lib" --java-options "--add-modules javafx.controls,javafx.media,javafx.swing" --java-options "--add-opens javafx.graphics/com.sun.javafx.sg.prism=ALL-UNNAMED"

if exist "%DIST%\icon.ico" (
    %JP% --icon "%DIST%\icon.ico"
) else (
    %JP%
)
if errorlevel 1 ( echo WARNING: jpackage app-image failed, will run via launcher & goto :verify )
echo     OK: App image built

:verify
:: ---- Step 9: Verify ------------------------------------------------------
echo [9/9] Verifying...
echo.
echo ============================================
echo   BUILD COMPLETE!
echo ============================================
echo.
echo   You can now launch HeadSet directly:
echo   - Simply double-click: %SRC%\HeadSet.exe
echo   - Or run: %SRC%\run.bat
echo   - Or widget: %SRC%\START_WIDGET_WINDOWS.bat
echo.
endlocal
goto :eof

:: ---- :download subroutine -----------------------------------------------
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
        del /f /q "%_DST%" 2>nul
    )
)
echo         WARNING: %_NM% unavailable
goto :eof
