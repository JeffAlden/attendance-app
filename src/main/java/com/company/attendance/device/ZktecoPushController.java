package com.company.attendance.device;

import com.company.attendance.domain.Direction;
import com.company.attendance.service.ScanService;
import lombok.RequiredArgsConstructor;
import org.slf4j.*;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * ZKTeco "ADMS / Push" protocol - used by SpeedFace and ProFace series.
 * On the device: Menu > Comm. > Cloud Server Setting > Server Address = this PC's IP, Port = 8080.
 * The device buffers scans while offline and re-sends them with their ORIGINAL timestamps.
 */
@RestController @RequestMapping("/iclock") @RequiredArgsConstructor
public class ZktecoPushController implements BiometricDevice {
    private static final Logger log = LoggerFactory.getLogger(ZktecoPushController.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final ScanService scans;
    private final com.company.attendance.service.ReaderSettingsService readerSettings;
    private final com.company.attendance.repository.ScanLogRepository logs;

    private void authorize(String sn) {
        var config = readerSettings.current();
        if (!"PUSH".equals(config.getConnectionMode()))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Push mode is disabled");
        if (!config.getSerialNumber().isBlank() && !config.getSerialNumber().equals(sn))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "Reader serial is not configured");
    }

    @Override public String brand() { return "ZKTeco"; }

    /** Device handshake. */
    @GetMapping(value = "/cdata", produces = MediaType.TEXT_PLAIN_VALUE)
    public String handshake(@RequestParam("SN") String sn) {
        authorize(sn);
        readerSettings.recordContact(sn);
        log.info("ZKTeco device connected: {}", sn);
        return "GET OPTION FROM: " + sn + "\nStamp=9999\nOpStamp=9999\nErrorDelay=60\nDelay=10\n"
             + "TransTimes=00:00;14:05\nTransInterval=1\nTransFlag=TransData AttLog\nRealtime=1\nEncrypt=0\n";
    }

    /** Attendance records: one per line  PIN \t yyyy-MM-dd HH:mm:ss \t status \t verify ... */
    @PostMapping(value = "/cdata", produces = MediaType.TEXT_PLAIN_VALUE)
    public synchronized String receive(@RequestParam("SN") String sn,
                          @RequestParam(value = "table", required = false) String table,
                          @RequestBody(required = false) String body) {
        authorize(sn);
        readerSettings.recordContact(sn);
        int count = 0;
        if ("ATTLOG".equalsIgnoreCase(table) && body != null) {
            Direction fixed = readerSettings.direction(sn);
            for (String line : body.split("\\r?\\n")) {
                String[] p = line.trim().split("\\t");
                if (p.length < 2) continue;
                try {
                    var timestamp = LocalDateTime.parse(p[1], FMT);
                    if (!logs.existsByDeviceIdAndRawCodeAndTimestamp(sn, p[0], timestamp))
                        scans.process(p[0], sn, timestamp, fixed, "ZKTECO");
                    count++;
                } catch (java.time.format.DateTimeParseException ex) {
                    throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid attendance timestamp");
                }
            }
        }
        return "OK: " + count;
    }

    /** Device polls for commands - nothing to send. */
    @GetMapping(value = "/getrequest", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getRequest(@RequestParam("SN") String sn) { authorize(sn); readerSettings.recordContact(sn); return "OK"; }

    @PostMapping(value = "/devicecmd", produces = MediaType.TEXT_PLAIN_VALUE)
    public String commandResult(@RequestParam("SN") String sn) { authorize(sn); readerSettings.recordContact(sn); return "OK"; }

}
