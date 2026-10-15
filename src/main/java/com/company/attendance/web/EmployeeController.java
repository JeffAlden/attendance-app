package com.company.attendance.web;

import com.company.attendance.config.AppProperties;
import com.company.attendance.domain.Employee;
import com.company.attendance.repository.*;
import com.company.attendance.service.AuditService;
import com.opencsv.CSVReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.security.Principal;
import java.util.List;

@Controller @RequiredArgsConstructor
public class EmployeeController {
    private final EmployeeRepository employees;
    private final ShiftRepository shifts;
    private final AppProperties props;
    private final AuditService audit;

    @GetMapping("/employees")
    String list(@RequestParam(required = false) Long edit, Model m) {
        m.addAttribute("employees", employees.findAll());
        m.addAttribute("shifts", shifts.findAll());
        m.addAttribute("emp", edit != null ? employees.findById(edit).orElse(new Employee()) : new Employee());
        return "employees";
    }

    @PostMapping("/employees")
    String save(@RequestParam(required = false) Long id, @RequestParam String code, @RequestParam String fullName,
                @RequestParam(required = false) String department, @RequestParam(required = false) String biometricId,
                @RequestParam(required = false) Long shiftId, @RequestParam(required = false) MultipartFile photo,
                Principal user) throws Exception {
        Employee e = id != null ? employees.findById(id).orElse(new Employee()) : new Employee();
        e.setCode(code.trim()); e.setFullName(fullName.trim()); e.setDepartment(department);
        e.setBiometricId(biometricId == null || biometricId.isBlank() ? code.trim() : biometricId.trim());
        e.setShift(shiftId != null ? shifts.findById(shiftId).orElse(null) : null);
        if (photo != null && !photo.isEmpty()) {
            Path dir = Path.of(props.getDataDir(), "photos"); Files.createDirectories(dir);
            String orig = photo.getOriginalFilename();
            String ext = orig != null && orig.contains(".") ? orig.substring(orig.lastIndexOf('.')) : ".jpg";
            String name = code.replaceAll("[^A-Za-z0-9_-]", "") + ext.replaceAll("[^A-Za-z0-9.]", "");
            Files.copy(photo.getInputStream(), dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            e.setPhotoPath(name);
        }
        employees.save(e);
        audit.log(user.getName(), id == null ? "EMPLOYEE_ADD" : "EMPLOYEE_EDIT", e.getCode());
        return "redirect:/employees";
    }

    @PostMapping("/employees/{id}/toggle")
    String toggle(@PathVariable Long id, Principal user) {
        employees.findById(id).ifPresent(e -> { e.setActive(!e.isActive()); employees.save(e);
            audit.log(user.getName(), e.isActive() ? "EMPLOYEE_ACTIVATE" : "EMPLOYEE_DEACTIVATE", e.getCode()); });
        return "redirect:/employees";
    }

    /** CSV columns: code,name,department,biometricId  (first row = header) */
    @PostMapping("/employees/import")
    String importCsv(@RequestParam MultipartFile file, Principal user) throws Exception {
        int n = 0;
        try (CSVReader r = new CSVReader(new InputStreamReader(file.getInputStream()))) {
            List<String[]> rows = r.readAll();
            for (String[] c : rows.stream().skip(1).toList()) {
                if (c.length < 2 || c[0].isBlank()) continue;
                Employee e = employees.findByCode(c[0].trim()).orElse(new Employee());
                e.setCode(c[0].trim()); e.setFullName(c[1].trim());
                e.setDepartment(c.length > 2 ? c[2].trim() : null);
                e.setBiometricId(c.length > 3 && !c[3].isBlank() ? c[3].trim() : c[0].trim());
                employees.save(e); n++;
            }
        }
        audit.log(user.getName(), "EMPLOYEE_IMPORT", n + " rows");
        return "redirect:/employees";
    }
}
