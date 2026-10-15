package com.company.attendance.device;

import com.company.attendance.config.AppProperties;
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
    private final AppProperties props;

    @Override public String brand() { return "ZKTeco"; }

    /** Device handshake. */
    @GetMapping(value = "/cdata", produces = MediaType.TEXT_PLAIN_VALUE)
    public String handshake(@RequestParam("SN") String sn) {
        log.info("ZKTeco device connected: {}", sn);
        return "GET OPTION FROM: " + sn + "\nStamp=9999\nOpStamp=9999\nErrorDelay=60\nDelay=10\n"
             + "TransTimes=00:00;14:05\nTransInterval=1\nTransFlag=TransData AttLog\nRealtime=1\nEncrypt=0\n";
    }

    /** Attendance records: one per line  PIN \t yyyy-MM-dd HH:mm:ss \t status \t verify ... */
    @PostMapping(value = "/cdata", produces = MediaType.TEXT_PLAIN_VALUE)
    public String receive(@RequestParam("SN") String sn,
                          @RequestParam(value = "table", required = false) String table,
                          @RequestBody(required = false) String body) {
        int count = 0;
        if ("ATTLOG".equalsIgnoreCase(table) && body != null) {
            Direction fixed = parse(props.getDeviceDirections().get(sn));
            for (String line : body.split("\\r?\\n")) {
                String[] p = line.trim().split("\\t");
                if (p.length < 2) continue;
                try {
                    scans.process(p[0], sn, LocalDateTime.parse(p[1], FMT), fixed, "ZKTECO");
                    count++;
                } catch (Exception ex) { log.warn("Bad ATTLOG line '{}': {}", line, ex.getMessage()); }
            }
        }
        return "OK: " + count;
    }

    /** Device polls for commands - nothing to send. */
    @GetMapping(value = "/getrequest", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getRequest(@RequestParam(value = "SN", required = false) String sn) { return "OK"; }

    private Direction parse(String s) { return s == null ? null : Direction.valueOf(s.trim().toUpperCase()); }
}
