@echo off
setlocal
cd /d "%~dp0"
set "HEADSET_WIDGET=1"
set "JAVABIN=java"
where java >nul 2>&1
if errorlevel 1 (
  if exist "C:\Java\jdk-21.0.13.1+2\bin\java.exe" (
    set "JAVABIN=C:\Java\jdk-21.0.13.1+2\bin\java.exe"
  ) else (
    echo Java 17 or newer is required.
    echo Install a JDK, then double-click this file again.
    pause
    exit /b 1
  )
)
echo Starting HeadSet music widget...
echo Keep this window open while listening, or use the system tray's Quit HeadSet option.
"%JAVABIN%" MusicServer.java
if errorlevel 1 (
  echo Could not start HeadSet. Make sure Java 17 or newer is installed.
  pause
)
