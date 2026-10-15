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
        "First IN", "Last OUT", "Hours", "Late", "Early Out", "Overtime", "Status", "Recording mode");
    private final EmployeeRepository employees;
    private final ScanLogRepository logs;

    public List<List<Object>> rows(LocalDate from, LocalDate to) {
        if (to.isBefore(from) || java.time.temporal.ChronoUnit.DAYS.between(from, to) > 365)
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,
                "Choose a summary date range of up to 366 days, with the end on or after the start.");
        List<List<Object>> out = new ArrayList<>();
        List<Employee> staff = employees.findByActiveTrue();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            Map<Long, List<ScanLog>> byEmp = logs
                .findByStatusAndTimestampBetweenOrderByTimestamp(ScanStatus.OK, d.atStartOfDay(), d.plusDays(2).atStartOfDay())
                .stream().filter(x -> x.getEmployee() != null).collect(Collectors.groupingBy(x -> x.getEmployee().getId()));
            boolean weekend = d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY;
            for (Employee e : staff) {
                Shift sh = e.getShift();
                boolean overnight = sh != null && sh.getEndTime().isBefore(sh.getStartTime());
                // Split overnight workdays halfway through the off-duty interval.
                LocalTime boundary = overnight ? sh.getEndTime().plusSeconds(
                    Duration.between(sh.getEndTime(), sh.getStartTime()).getSeconds() / 2) : LocalTime.MIDNIGHT;
                LocalDateTime windowStart = d.atTime(boundary), windowEnd = windowStart.plusDays(1);
                List<ScanLog> s = byEmp.getOrDefault(e.getId(), List.of()).stream()
                    .filter(x -> !x.getTimestamp().isBefore(windowStart) && x.getTimestamp().isBefore(windowEnd)).toList();
                String dept = e.getDepartment() == null ? "" : e.getDepartment();
                if (s.isEmpty()) {
                    if (!weekend) out.add(Arrays.asList(d.toString(), e.getCode(), e.getFullName(), dept, "", "", "", "", "", "", "ABSENT", ""));
                    continue;
                }
                LocalDateTime firstIn = null, lastOut = null;
                LocalDateTime open = null; Duration total = Duration.ZERO;
                for (ScanLog x : s) {
                    if (x.getDirection() == Direction.IN) {
                        if (firstIn == null) firstIn = x.getTimestamp();
                        if (open == null) open = x.getTimestamp();
                    } else {
                        if (open != null) { total = total.plus(Duration.between(open, x.getTimestamp())); open = null; }
                        lastOut = x.getTimestamp();
                    }
                }
                boolean entriesOnly = s.stream().anyMatch(ScanLog::isEntryOnly);
                String late = "", early = "", ot = "";
                if (sh != null && !entriesOnly) {
                    LocalDateTime scheduledStart = d.atTime(sh.getStartTime());
                    LocalDateTime scheduledEnd = (overnight ? d.plusDays(1) : d).atTime(sh.getEndTime());
                    if (firstIn != null && firstIn.isAfter(scheduledStart.plusMinutes(sh.getGraceMinutes())))
                        late = Duration.between(scheduledStart, firstIn).toMinutes() + " min";
                    if (lastOut != null && lastOut.isBefore(scheduledEnd))
                        early = Duration.between(lastOut, scheduledEnd).toMinutes() + " min";
                    if (lastOut != null && lastOut.isAfter(scheduledEnd))
                        ot = Duration.between(scheduledEnd, lastOut).toMinutes() + " min";
                }
                out.add(Arrays.asList(d.toString(), e.getCode(), e.getFullName(), dept,
                    firstIn == null ? "" : firstIn.toLocalTime().withNano(0).toString(),
                    entriesOnly ? "" : lastOut == null ? (open != null ? "(inside)" : "") : lastOut.toLocalTime().withNano(0).toString(),
                    entriesOnly ? "" : String.format(java.util.Locale.ROOT, "%.2f", total.toMinutes() / 60.0), late, early, ot,
                    entriesOnly ? "ENTRY RECORDED" : "PRESENT", entriesOnly ? "IN only (exit not recorded)" : "IN / OUT"));
            }
        }
        return out;
    }
}
