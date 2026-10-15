# Time & Attendance (Turnstile + ZKTeco SpeedFace / ProFace)

## Run
    mvn spring-boot:run          (or run-windows.bat)
Open http://localhost:8080 - logins: admin/admin123 (everything), hr/hr12345 (logs + export), guard/guard123 (dashboard).
**Change these passwords** (edit DataSeeder before first run).

## Local storage and everyday workflow
The default H2 database persists employees, shifts, users, and scan history on this PC across app restarts.
Source runs store data under `%USERPROFILE%\AttendanceApp`; the installer uses `C:\ProgramData\AttendanceApp`.
The `db` subfolder contains the database and `photos` contains uploaded employee photos.
Stop the app before copying the entire data folder for a backup. Do not use the generated `target` folder for your everyday data.
PostgreSQL can replace H2 later using the existing datasource settings; no additional storage service is required for this prototype.

1. Create shifts in **Shifts**; edit existing shifts or delete unused ones. Assigned shifts must be reassigned before deletion. End times before start times are overnight shifts.
2. Add employees in **Employees**, optionally assigning a shift and uploading a photo (up to 5 MB). Employee codes and device PINs must identify only one employee.
3. CSV import uses the header `code,name,department,biometricId`. Matching employee codes update existing people while preserving their shift, photo, and active status. An invalid row cancels the whole import.
4. Use **Simulator** temporarily to try IN, OUT, automatic toggling, and denied scans. Wait 10 seconds between scans of the same employee to avoid duplicate suppression.
5. Use **Dashboard** to see counts and current occupancy, and **Logs** to filter scans or export CSV, Excel, and daily summaries. Overnight summaries are attributed to the shift's start date, splitting workdays halfway through the off-duty interval.

After source changes, rebuild and restart the app so the running Java server uses the new code.
Run `mvn test` for the isolated workflow tests; they use an in-memory test database and do not alter your local records.

## Reader settings and free exit
The admin-only **Settings** tab sits between Shifts and Simulator. Reader details and operation mode are stored in the local database.
Choose **IN only** for an entrance reader with pushbutton exit: every accepted scan records IN, automatic OUT toggling and anti-passback are bypassed, and the dashboard lists unique employees who entered today. Duplicate suppression still applies.
Choose **IN and OUT** when exit scans are available. Switching back starts automatic direction tracking at IN for employees whose latest scan was entry-only; untracked exits are never inferred.
Historical scans are preserved. Entry-only scans are marked in daily summaries, with exit times, hours, and overtime left blank. The **Entry register (Excel)** report groups accepted IN scans by employee and calendar date, with first/latest entry and entry count, and honors the date/name/department filters in Logs.

Reader IP and reader port are reference details for the device. This app currently receives ZKTeco ADMS/Push traffic; saving an IP does not establish an SDK connection or provision the device. Configure the reader's ADMS server address to this PC's fixed LAN IP and its server port to the running app port shown in Settings. The configured reader serial controls its IN/OUT direction; readers in `application.yml` remain supported as a fallback.
Settings shows the last received reader contact, not a continuous online/offline check.

**Demo fingerprint enrollment** saves only an employee/finger label and timestamp. It can be cleared, stores no fingerprint templates, and does not authorize or enroll anyone on hardware. Complete real enrollment on the physical reader using the employee's Device User ID.

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
