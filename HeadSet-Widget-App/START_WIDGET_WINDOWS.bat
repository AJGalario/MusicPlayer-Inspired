@echo off
setlocal
cd /d "%~dp0"
set "HEADSET_WIDGET=1"
set "JAVABIN=java"

:: 1. Check if java is already in PATH
where java >nul 2>&1
if not errorlevel 1 goto :found_java

:: 2. Check JAVA_HOME
if defined JAVA_HOME (
  if exist "%JAVA_HOME%\bin\java.exe" (
    set "JAVABIN=%JAVA_HOME%\bin\java.exe"
    goto :found_java
  )
)

:: 3. Check known user and lab JDK installations
if exist "C:\Users\Aaron John\jdk-25.0.2\bin\java.exe" (
  set "JAVABIN=C:\Users\Aaron John\jdk-25.0.2\bin\java.exe"
  goto :found_java
)
if exist "C:\Java\jdk-21.0.13.1+2\bin\java.exe" (
  set "JAVABIN=C:\Java\jdk-21.0.13.1+2\bin\java.exe"
  goto :found_java
)

:: 4. Search Program Files for any installed JDK
for /d %%D in ("C:\Program Files\Java\jdk*" "C:\Program Files\Eclipse Adoptium\jdk*" "C:\Java\jdk*") do (
  if exist "%%D\bin\java.exe" (
    set "JAVABIN=%%D\bin\java.exe"
    goto :found_java
  )
)

echo Java 17 or newer is required.
echo Install a JDK, then double-click this file again.
pause
exit /b 1

:found_java
echo Starting HeadSet music widget...
echo Keep this window open while listening, or use the system tray's Quit HeadSet option.
"%JAVABIN%" MusicServer.java
if errorlevel 1 (
  echo Could not start HeadSet. Make sure Java 17 or newer is installed.
  pause
)
