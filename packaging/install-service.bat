@echo off
REM Run as Administrator. Installs the app as a Windows service so it starts on boot.
REM 1) Download WinSW (WinSW-x64.exe) from github.com/winsw/winsw/releases
REM 2) Rename it to AttendanceApp-service.exe and put it in this folder next to AttendanceApp-service.xml
REM 3) Put attendance-app.jar in this folder too.
netsh advfirewall firewall add rule name="AttendanceApp 8080" dir=in action=allow protocol=TCP localport=8080
AttendanceApp-service.exe install
AttendanceApp-service.exe start
