package com.company.attendance.service;

import com.company.attendance.domain.*;
import com.company.attendance.repository.ScanLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service @RequiredArgsConstructor
public class DashboardService {
    private final ScanLogRepository logs;

    public record Row(String name, String code, String dept, String photo, String direction, String status, String time) {}

    public Map<String, Object> data() {
        LocalDateTime a = LocalDate.now().atStartOfDay(), b = a.plusDays(1);
        List<ScanLog> inside = logs.inside(ScanStatus.OK, Direction.IN);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("inside", inside.size());
        m.put("totalIn", logs.countByDirectionAndStatusAndTimestampBetween(Direction.IN, ScanStatus.OK, a, b));
        m.put("totalOut", logs.countByDirectionAndStatusAndTimestampBetween(Direction.OUT, ScanStatus.OK, a, b));
        m.put("denied", logs.countByStatusAndTimestampBetween(ScanStatus.DENIED, a, b));
        m.put("recent", logs.findTop20ByOrderByTimestampDesc().stream().map(this::row).toList());
        m.put("insideList", inside.stream().map(this::row).toList());
        return m;
    }

    private Row row(ScanLog l) {
        Employee e = l.getEmployee();
        return new Row(
            e != null ? e.getFullName() : "Unknown (" + l.getRawCode() + ")",
            e != null ? e.getCode() : l.getRawCode(),
            e != null && e.getDepartment() != null ? e.getDepartment() : "",
            e != null && e.getPhotoPath() != null ? "/photos/" + e.getPhotoPath() : null,
            l.getDirection() != null ? l.getDirection().name() : "",
            l.getStatus().name(),
            l.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
    }
}
