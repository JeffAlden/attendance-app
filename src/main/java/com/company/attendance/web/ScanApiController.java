package com.company.attendance.web;

import com.company.attendance.domain.*;
import com.company.attendance.service.ScanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.*;

@RestController @RequiredArgsConstructor
public class ScanApiController {
    private final ScanService scans;

    public record ScanRequest(String deviceId, String employeeCode, LocalDateTime timestamp, Direction direction) {}

    /** POST /api/scans  {deviceId, employeeCode, timestamp?, direction?} */
    @PostMapping("/api/scans")
    public Map<String, Object> scan(@RequestBody ScanRequest r) {
        ScanLog l = scans.process(r.employeeCode(), r.deviceId(), r.timestamp(), r.direction(), "API");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("status", l.getStatus());
        m.put("direction", l.getDirection());
        m.put("employee", l.getEmployee() != null ? l.getEmployee().getFullName() : null);
        m.put("timestamp", l.getTimestamp());
        return m;
    }
}
