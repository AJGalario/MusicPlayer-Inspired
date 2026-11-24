param()

$JDK      = "C:\Users\Aaron John\jdk-25.0.2"
$JAVAC    = "$JDK\bin\javac.exe"
$JAR      = "$JDK\bin\jar.exe"
$JPACKAGE = "$JDK\bin\jpackage.exe"
$SRC_DIR  = "C:\Users\Aaron John\Downloads\neon-player\neon-player"
$BUILD    = "C:\HeadSet-Build\_compile"
$DIST     = "C:\HeadSet-Build\_dist"
$OUT      = "C:\HeadSet-Build\output"
$JFX_LIB  = "C:\HeadSet-Build\javafx-lib"

# JavaFX 21 platform JARs from Maven Central (include native Windows DLLs)
$JFX_VER  = "21"
$MAVEN    = "https://repo1.maven.org/maven2/org/openjfx"
$JFX_MODULES = @("javafx-base","javafx-controls","javafx-fxml","javafx-graphics","javafx-swing","javafx-web","javafx-media")

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  HeadSet - Native Desktop App Builder"      -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# ---- pre-flight ----
if (-not (Test-Path $JAVAC)) {
    Write-Host "ERROR: javac not found at $JAVAC" -ForegroundColor Red; exit 1
}
if (-not (Test-Path $JPACKAGE)) {
    Write-Host "ERROR: jpackage not found" -ForegroundColor Red; exit 1
}

# ---- step 1: clean ----
Write-Host "[1/8] Cleaning..." -ForegroundColor Yellow
Remove-Item -Recurse -Force "C:\HeadSet-Build" -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force $BUILD   | Out-Null
New-Item -ItemType Directory -Force $DIST    | Out-Null
New-Item -ItemType Directory -Force $OUT     | Out-Null
New-Item -ItemType Directory -Force $JFX_LIB | Out-Null
Write-Host "    OK" -ForegroundColor Green

# ---- step 2: download JavaFX JARs from Maven Central ----
Write-Host "[2/8] Downloading JavaFX 21 JARs from Maven Central..." -ForegroundColor Yellow
Write-Host "    (Downloading 7 modules x 2 JARs each — please wait)" -ForegroundColor DarkGray

$downloadFailed = $false
foreach ($mod in $JFX_MODULES) {
    # Regular JAR (classes)
    $plainJar  = "$mod-$JFX_VER.jar"
    $plainUrl  = "$MAVEN/$mod/$JFX_VER/$plainJar"
    $plainDest = "$JFX_LIB\$plainJar"

    # Windows native JAR (contains .dll files)
    $winJar    = "$mod-$JFX_VER-win.jar"
    $winUrl    = "$MAVEN/$mod/$JFX_VER/$winJar"
    $winDest   = "$JFX_LIB\$winJar"

    Write-Host "    -> $mod" -ForegroundColor DarkGray
    curl.exe -s -S -L --max-time 60 --retry 3 --retry-delay 2 -o $plainDest $plainUrl
    if ($LASTEXITCODE -ne 0) {
        Write-Host "    WARN: Failed to download $plainJar (exit $LASTEXITCODE)" -ForegroundColor DarkYellow
        $downloadFailed = $true
    }
    curl.exe -s -S -L --max-time 60 --retry 3 --retry-delay 2 -o $winDest $winUrl
    if ($LASTEXITCODE -ne 0) {
        Write-Host "    WARN: Failed to download $winJar (exit $LASTEXITCODE)" -ForegroundColor DarkYellow
    }
}

if ($downloadFailed) {
    Write-Host "    Some JARs failed to download. Build may still work." -ForegroundColor DarkYellow
}

$JFX_JARS = (Get-ChildItem "$JFX_LIB\*.jar" | Select-Object -ExpandProperty FullName) -join ";"
$jarCount  = (Get-ChildItem "$JFX_LIB\*.jar").Count
Write-Host "    OK: $jarCount JARs downloaded to $JFX_LIB" -ForegroundColor Green

# ---- step 3: compile ----
Write-Host "[3/8] Compiling Java sources..." -ForegroundColor Yellow
$compileArgs = @(
    "--module-path", $JFX_LIB,
    "--add-modules", "javafx.controls,javafx.web,javafx.swing",
    "-cp", $JFX_JARS,
    "-d", $BUILD,
    "$SRC_DIR\MusicServer.java",
    "$SRC_DIR\HeadSetApp.java"
)
& $JAVAC @compileArgs
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: Compilation failed" -ForegroundColor Red; exit 1
}
Write-Host "    OK: Compiled" -ForegroundColor Green

# ---- step 4: jar ----
Write-Host "[4/8] Creating headset.jar..." -ForegroundColor Yellow
$mfPath = "$BUILD\MANIFEST.MF"
Set-Content -Path $mfPath -Value "Main-Class: HeadSetApp`r`n" -Encoding ASCII
Push-Location $BUILD
& $JAR cfm "$DIST\headset.jar" $mfPath .
$jarExit = $LASTEXITCODE
Pop-Location
if ($jarExit -ne 0) {
    Write-Host "ERROR: JAR failed" -ForegroundColor Red; exit 1
}
Write-Host "    OK: headset.jar created" -ForegroundColor Green

# ---- step 5: copy resources ----
Write-Host "[5/8] Copying web files and music folder..." -ForegroundColor Yellow
Copy-Item -Recurse -Force "$SRC_DIR\public" "$DIST\public"
if (Test-Path "$SRC_DIR\music") {
    Copy-Item -Recurse -Force "$SRC_DIR\music" "$DIST\music"
} else {
    New-Item -ItemType Directory -Force "$DIST\music" | Out-Null
}
# Put all JavaFX JARs in dist/lib so jpackage bundles them
New-Item -ItemType Directory -Force "$DIST\lib" | Out-Null
Copy-Item "$JFX_LIB\*.jar" "$DIST\lib\"
Write-Host "    OK: Resources copied" -ForegroundColor Green

# ---- step 6: icon ----
Write-Host "[6/8] Generating app icon..." -ForegroundColor Yellow
$ICON_ICO = "$DIST\icon.ico"
$iconOK   = $false
try {
    Add-Type -AssemblyName System.Drawing
    $bmp = New-Object System.Drawing.Bitmap(256, 256)
    $g   = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $bg  = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 124, 111, 205))
    $g.FillEllipse($bg, 0, 0, 255, 255)
    $pen = New-Object System.Drawing.Pen([System.Drawing.Color]::White, 20)
    $pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.EndCap   = [System.Drawing.Drawing2D.LineCap]::Round
    $g.DrawArc($pen, 50, 44, 156, 110, 180, 180)
    $ear = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::White)
    $g.FillRectangle($ear, 30,  130, 40, 56); $g.FillEllipse($ear, 26, 118, 48, 28); $g.FillEllipse($ear, 26, 158, 48, 28)
    $g.FillRectangle($ear, 186, 130, 40, 56); $g.FillEllipse($ear, 182, 118, 48, 28); $g.FillEllipse($ear, 182, 158, 48, 28)
    $g.Dispose()
    $hicon = $bmp.GetHicon()
    $icon  = [System.Drawing.Icon]::FromHandle($hicon)
    $fs    = [System.IO.File]::OpenWrite($ICON_ICO)
    $icon.Save($fs); $fs.Close(); $bmp.Dispose()
    $iconOK = $true
    Write-Host "    OK: Icon generated" -ForegroundColor Green
} catch {
    Write-Host "    Skipped icon" -ForegroundColor DarkYellow
}

# ---- step 7: jpackage ----
Write-Host "[7/8] Building EXE with jpackage (this takes ~1-2 min)..." -ForegroundColor Yellow

$jpackArgs = @(
    "--type",         "app-image",
    "--name",         "HeadSet",
    "--input",        $DIST,
    "--main-jar",     "headset.jar",
    "--main-class",   "HeadSetApp",
    "--dest",         $OUT,
    "--app-version",  "1.0.0",
    "--description",  "HeadSet Music Player",
    "--vendor",       "HeadSet",
    "--java-options", "-Xmx256m",
    "--java-options", "-Djava.awt.headless=false",
    "--java-options", "--module-path lib",
    "--java-options", "--add-modules javafx.controls,javafx.web,javafx.swing",
    "--java-options", "--add-opens javafx.graphics/com.sun.javafx.sg.prism=ALL-UNNAMED"
)
if ($iconOK) {
    $jpackArgs += "--icon"
    $jpackArgs += $ICON_ICO
}

& $JPACKAGE @jpackArgs
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERROR: jpackage failed" -ForegroundColor Red; exit 1
}
Write-Host "    OK: EXE created" -ForegroundColor Green

# ---- step 8: verify ----
Write-Host "[8/8] Verifying..." -ForegroundColor Yellow
$EXE = "$OUT\HeadSet\HeadSet.exe"
if (-not (Test-Path $EXE)) {
    Write-Host "ERROR: HeadSet.exe not found" -ForegroundColor Red; exit 1
}
$sizeMB = [math]::Round((Get-ChildItem -Recurse "$OUT\HeadSet" | Measure-Object Length -Sum).Sum / 1MB, 0)

Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "  BUILD SUCCESSFUL!" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host ""
Write-Host "  EXE      : $EXE" -ForegroundColor White
Write-Host "  App size : $sizeMB MB" -ForegroundColor White
Write-Host ""
Write-Host "  Double-click HeadSet.exe" -ForegroundColor Cyan
Write-Host "  Opens as its OWN window - no browser needed!" -ForegroundColor Cyan
Write-Host "  Right-click tray icon to Quit." -ForegroundColor Cyan
Write-Host ""

Start-Process explorer.exe "$OUT\HeadSet"
