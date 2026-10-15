package com.company.attendance.web;

import com.company.attendance.domain.*;
import com.company.attendance.repository.ScanLogRepository;
import com.company.attendance.service.*;
import jakarta.persistence.criteria.*;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.security.Principal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Controller @RequiredArgsConstructor
public class LogController {
    private static final List<String> HEADERS = List.of("Time", "Code", "Name", "Department", "Direction", "Status", "Device", "Source");
    private final ScanLogRepository logs;
    private final SummaryService summary;
    private final ExportService export;
    private final AuditService audit;

    @GetMapping("/logs")
    String page(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                @RequestParam(required = false) String q, @RequestParam(required = false) String department,
                @RequestParam(required = false) Direction direction,
                @RequestParam(defaultValue = "0") int page, Model m) {
        if (from == null) from = LocalDate.now();
        if (to == null) to = from;
        Page<ScanLog> p = logs.findAll(spec(from, to, q, department, direction),
            PageRequest.of(Math.max(0, page), 25, Sort.by(Sort.Direction.DESC, "timestamp")));
        m.addAttribute("page", p); m.addAttribute("from", from); m.addAttribute("to", to);
        m.addAttribute("q", q); m.addAttribute("department", department); m.addAttribute("direction", direction);
        return "logs";
    }

    @GetMapping("/logs/export/{fmt}")
    void export(@PathVariable String fmt,
                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                @RequestParam(required = false) String q, @RequestParam(required = false) String department,
                @RequestParam(required = false) Direction direction,
                Principal user, HttpServletResponse res) throws IOException {
        if (from == null) from = LocalDate.now();
        if (to == null) to = from;
        List<List<Object>> rows = new ArrayList<>();
        DateTimeFormatter f = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (ScanLog l : logs.findAll(spec(from, to, q, department, direction), Sort.by("timestamp"))) {
            Employee e = l.getEmployee();
            rows.add(Arrays.asList(l.getTimestamp().format(f), e != null ? e.getCode() : l.getRawCode(),
                e != null ? e.getFullName() : "UNKNOWN", e != null ? e.getDepartment() : "",
                l.getDirection(), l.getStatus(), l.getDeviceId(), l.getSource()));
        }
        audit.log(user.getName(), "EXPORT_LOGS", fmt + " " + from + " to " + to);
        send(res, fmt, "logs_" + from + "_" + to, "Logs", HEADERS, rows);
    }

    @GetMapping("/logs/summary")
    void summary(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                 Principal user, HttpServletResponse res) throws IOException {
        if (from == null) from = LocalDate.now();
        if (to == null) to = from;
        audit.log(user.getName(), "EXPORT_SUMMARY", from + " to " + to);
        send(res, "xlsx", "daily_summary_" + from + "_" + to, "Summary", SummaryService.HEADERS, summary.rows(from, to));
    }

    @GetMapping("/logs/entries")
    void entries(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                 @RequestParam(required = false) String q, @RequestParam(required = false) String department,
                 Principal user, HttpServletResponse res) throws IOException {
        if (from == null) from = LocalDate.now();
        if (to == null) to = from;
        Map<String, List<ScanLog>> groups = new LinkedHashMap<>();
        for (ScanLog log : logs.findAll(spec(from, to, q, department, Direction.IN), Sort.by("timestamp"))) {
            if (log.getStatus() != ScanStatus.OK || log.getEmployee() == null) continue;
            String key = log.getTimestamp().toLocalDate() + ":" + log.getEmployee().getId();
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(log);
        }
        List<List<Object>> rows = new ArrayList<>();
        for (List<ScanLog> entries : groups.values()) {
            ScanLog first = entries.getFirst(), last = entries.getLast();
            Employee employee = first.getEmployee();
            rows.add(Arrays.asList(first.getTimestamp().toLocalDate().toString(), employee.getCode(), employee.getFullName(),
                employee.getDepartment(), first.getTimestamp().toLocalTime().toString(), last.getTimestamp().toLocalTime().toString(),
                entries.size(), "Exit not included in this entry register"));
        }
        audit.log(user.getName(), "EXPORT_ENTRIES", from + " to " + to);
        send(res, "xlsx", "entry_register_" + from + "_" + to, "Entry register",
            List.of("Date", "Code", "Name", "Department", "First IN", "Latest IN", "Accepted entries", "Notes"), rows);
    }

    private void send(HttpServletResponse res, String fmt, String name, String sheet,
                      List<String> h, List<List<Object>> rows) throws IOException {
        if (!fmt.equalsIgnoreCase("csv") && !fmt.equalsIgnoreCase("xlsx"))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Choose CSV or XLSX export.");
        boolean csv = fmt.equalsIgnoreCase("csv");
        res.setContentType(csv ? "text/csv" : "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        res.setHeader("Content-Disposition", "attachment; filename=" + name + (csv ? ".csv" : ".xlsx"));
        if (csv) export.csv(res.getOutputStream(), h, rows); else export.xlsx(res.getOutputStream(), sheet, h, rows);
    }

    private Specification<ScanLog> spec(LocalDate from, LocalDate to, String q, String dept, Direction dir) {
        if (to.isBefore(from))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "End date must be on or after start date.");
        return (r, cq, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.greaterThanOrEqualTo(r.<LocalDateTime>get("timestamp"), from.atStartOfDay()));
            p.add(cb.lessThan(r.<LocalDateTime>get("timestamp"), to.plusDays(1).atStartOfDay()));
            if (dir != null) p.add(cb.equal(r.get("direction"), dir));
            boolean hasQ = q != null && !q.isBlank(), hasD = dept != null && !dept.isBlank();
            if (hasQ || hasD) {
                Join<ScanLog, Employee> e = r.join("employee", JoinType.LEFT);
                if (hasQ) {
                    String like = "%" + q.trim().toLowerCase() + "%";
                    p.add(cb.or(cb.like(cb.lower(e.<String>get("fullName")), like), cb.like(cb.lower(e.<String>get("code")), like),
                                cb.like(cb.lower(r.<String>get("rawCode")), like)));
                }
                if (hasD) p.add(cb.equal(cb.lower(e.<String>get("department")), dept.trim().toLowerCase()));
            }
            return cb.and(p.toArray(new Predicate[0]));
        };
    }
}
