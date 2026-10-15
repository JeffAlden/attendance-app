package com.company.attendance.service;

import com.company.attendance.domain.*;
import com.company.attendance.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/** Daily summary: first IN, last OUT, hours, Late / Early Out / Overtime / Absent (weekdays only). */
@Service @RequiredArgsConstructor
public class SummaryService {
    public static final List<String> HEADERS = List.of("Date", "Code", "Name", "Department",
        "First IN", "Last OUT", "Hours", "Late", "Early Out", "Overtime", "Status");
    private final EmployeeRepository employees;
    private final ScanLogRepository logs;

    public List<List<Object>> rows(LocalDate from, LocalDate to) {
        List<List<Object>> out = new ArrayList<>();
        List<Employee> staff = employees.findByActiveTrue();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            Map<Long, List<ScanLog>> byEmp = logs
                .findByStatusAndTimestampBetweenOrderByTimestamp(ScanStatus.OK, d.atStartOfDay(), d.plusDays(1).atStartOfDay())
                .stream().collect(Collectors.groupingBy(x -> x.getEmployee().getId()));
            boolean weekend = d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY;
            for (Employee e : staff) {
                List<ScanLog> s = byEmp.getOrDefault(e.getId(), List.of());
                String dept = e.getDepartment() == null ? "" : e.getDepartment();
                if (s.isEmpty()) {
                    if (!weekend) out.add(Arrays.asList(d.toString(), e.getCode(), e.getFullName(), dept, "", "", "", "", "", "", "ABSENT"));
                    continue;
                }
                LocalTime firstIn = null, lastOut = null;
                LocalDateTime open = null; Duration total = Duration.ZERO;
                for (ScanLog x : s) {
                    if (x.getDirection() == Direction.IN) {
                        if (firstIn == null) firstIn = x.getTimestamp().toLocalTime();
                        if (open == null) open = x.getTimestamp();
                    } else {
                        if (open != null) { total = total.plus(Duration.between(open, x.getTimestamp())); open = null; }
                        lastOut = x.getTimestamp().toLocalTime();
                    }
                }
                String late = "", early = "", ot = "";
                Shift sh = e.getShift();
                if (sh != null) {
                    if (firstIn != null && firstIn.isAfter(sh.getStartTime().plusMinutes(sh.getGraceMinutes())))
                        late = Duration.between(sh.getStartTime(), firstIn).toMinutes() + " min";
                    if (lastOut != null && lastOut.isBefore(sh.getEndTime()))
                        early = Duration.between(lastOut, sh.getEndTime()).toMinutes() + " min";
                    if (lastOut != null && lastOut.isAfter(sh.getEndTime()))
                        ot = Duration.between(sh.getEndTime(), lastOut).toMinutes() + " min";
                }
                out.add(Arrays.asList(d.toString(), e.getCode(), e.getFullName(), dept,
                    firstIn == null ? "" : firstIn.withNano(0).toString(),
                    lastOut == null ? (open != null ? "(inside)" : "") : lastOut.withNano(0).toString(),
                    String.format("%.2f", total.toMinutes() / 60.0), late, early, ot, "PRESENT"));
            }
        }
        return out;
    }
}
