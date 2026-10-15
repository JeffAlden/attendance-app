# Time & Attendance (Turnstile + ZKTeco SpeedFace / ProFace)

## Run
    mvn spring-boot:run          (or run-windows.bat)
Open http://localhost:8080 - logins: admin/admin123 (everything), hr/hr12345 (logs + export), guard/guard123 (dashboard).
**Change these passwords** (edit DataSeeder before first run).

## Connect a ZKTeco SpeedFace / ProFace reader (Push / ADMS)
1. Give the PC a fixed IP; allow TCP 8080 in Windows Firewall (install-service.bat does it).
2. On the device: Menu > Comm. > Cloud Server Setting -> Server Address = PC IP, Server Port = 8080, HTTPS off.
3. Enroll users on the device with a User ID equal to the employee Code (or the "Device User ID" field).
4. Entry reader + exit reader? Put each reader's serial number in application.yml:
   `attendance.device-directions: { SERIAL_IN: IN, SERIAL_OUT: OUT }`. Unlisted readers toggle IN/OUT.
5. Scans made while the PC is off are buffered by the device and arrive later with their original timestamps.
The console shows "ZKTeco device connected: <serial>" when it works.

## API (basic auth, ADMIN)
    curl -u admin:admin123 -H "Content-Type: application/json" \
      -d '{"deviceId":"GATE1","employeeCode":"E001","direction":"IN"}' http://localhost:8080/api/scans
`timestamp` (ISO, e.g. 2026-10-15T08:01:00) and `direction` are optional.

## Windows installer
`packaging\build-installer.bat` -> dist\AttendanceApp-1.0.0.exe (needs JDK 21 + WiX 3.x).
Background service: packaging\install-service.bat (WinSW). Data folder: C:\ProgramData\AttendanceApp.

## PostgreSQL
Edit the datasource block in application.yml (commented example included).
