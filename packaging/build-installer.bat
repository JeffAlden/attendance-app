@echo off
REM Builds a Windows installer (.exe) with a bundled Java runtime.
REM Requires: JDK 21 (jpackage) + WiX Toolset 3.x on PATH + Maven.
cd /d %~dp0\..
call mvn clean package -DskipTests || exit /b 1
mkdir target\input
copy target\attendance-app.jar target\input\ >nul
jpackage --type exe --name AttendanceApp --app-version 1.0.0 ^
  --input target\input --main-jar attendance-app.jar ^
  --main-class org.springframework.boot.loader.launch.JarLauncher ^
  --java-options "-Dattendance.data-dir=C:/ProgramData/AttendanceApp" ^
  --win-menu --win-shortcut --win-dir-chooser --dest dist
echo Installer created in dist\
