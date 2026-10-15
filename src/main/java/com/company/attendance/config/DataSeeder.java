package com.company.attendance.config;

import com.company.attendance.domain.*;
import com.company.attendance.repository.*;
import com.company.attendance.service.ScanService;
import lombok.RequiredArgsConstructor;
import org.slf4j.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.time.*;

/**
 * First run only: default users, one shift, 10 sample employees and a few
 * scans.
 */
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private final AppUserRepository users;
    private final EmployeeRepository employees;
    private final ShiftRepository shifts;
    private final ScanService scans;
    private final PasswordEncoder enc;

    @Override
    public void run(String... args) {
        if (users.count() == 0) {
            add("admin", "admin123", Role.ADMIN);
            add("hr", "hr12345", Role.HR);
            add("guard", "guard123", Role.GUARD);
            log.warn("Default users created: admin/admin123, hr/hr12345, guard/guard123 - CHANGE THESE PASSWORDS");
        }
        if (employees.count() > 0)
            return;
        Shift s = new Shift();
        s.setName("Day shift");
        s.setStartTime(LocalTime.of(8, 0));
        s.setEndTime(LocalTime.of(17, 0));
        s.setGraceMinutes(10);
        shifts.save(s);
        String[] names = { "Juan Dela Cruz", "Maria Santos", "Pedro Reyes", "Ana Garcia", "Jose Ramos",
                "Liza Mendoza", "Mark Torres", "Grace Flores", "Paul Castillo", "Rosa Navarro" };
        String[] depts = { "Production", "Production", "QA", "QA", "Warehouse", "Warehouse", "HR", "Admin",
                "Maintenance", "Maintenance" };
        for (int i = 0; i < 10; i++) {
            Employee e = new Employee();
            e.setCode(String.format("E%03d", i + 1));
            e.setBiometricId(String.valueOf(i + 1));
            e.setFullName(names[i]);
            e.setDepartment(depts[i]);
            e.setShift(s);
            employees.save(e);
        }
        LocalDateTime now = LocalDateTime.now();
        for (int i = 1; i <= 6; i++) {
            scans.process("E00" + i, "SEED", now.minusHours(4).plusMinutes(i), Direction.IN, "SEED");
            if (i <= 3)
                scans.process("E00" + i, "SEED", now.minusHours(1).plusMinutes(i), Direction.OUT, "SEED");
        }
        scans.process("9999", "SEED", now.minusHours(3), null, "SEED"); // unknown -> DENIED
    }

    private void add(String u, String p, Role r) {
        AppUser a = new AppUser();
        a.setUsername(u);
        a.setPasswordHash(enc.encode(p));
        a.setRole(r);
        users.save(a);
    }
}
