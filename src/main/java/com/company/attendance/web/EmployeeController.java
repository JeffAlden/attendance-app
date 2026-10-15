package com.company.attendance.web;

import com.company.attendance.domain.Employee;
import com.company.attendance.repository.*;
import com.company.attendance.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.security.Principal;

@Controller @RequiredArgsConstructor
public class EmployeeController {
    private final EmployeeRepository employees;
    private final ShiftRepository shifts;
    private final EmployeeService employeeService;
    private final AuditService audit;

    private String render(Model model, Employee form) {
        model.addAttribute("employees", employees.findAll());
        model.addAttribute("shifts", shifts.findAll());
        model.addAttribute("emp", form);
        return "employees";
    }

    @GetMapping("/employees")
    String list(@RequestParam(required = false) Long edit, Model model) {
        Employee form = edit == null ? new Employee() : employees.findById(edit).orElse(null);
        if (form == null) {
            model.addAttribute("error", "Employee no longer exists.");
            form = new Employee();
        }
        return render(model, form);
    }

    @PostMapping("/employees")
    String save(@RequestParam(required = false) Long id, @RequestParam String code, @RequestParam String fullName,
                @RequestParam(required = false) String department, @RequestParam(required = false) String biometricId,
                @RequestParam(required = false) Long shiftId, @RequestParam(required = false) MultipartFile photo,
                Principal user, Model model, RedirectAttributes flash) {
        Employee form = new Employee(); form.setId(id); form.setCode(code); form.setFullName(fullName);
        form.setDepartment(department); form.setBiometricId(biometricId);
        if (shiftId != null) form.setShift(shifts.findById(shiftId).orElse(null));
        try {
            Employee saved = employeeService.save(form, shiftId, photo);
            audit.log(user.getName(), id == null ? "EMPLOYEE_ADD" : "EMPLOYEE_EDIT", saved.getCode());
            flash.addFlashAttribute("success", id == null ? "Employee added." : "Employee updated.");
            return "redirect:/employees";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
        } catch (java.io.IOException ex) {
            model.addAttribute("error", "Unable to save the photo. Please try again.");
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            model.addAttribute("error", "Employee code is already in use. Please choose another code.");
        }
        return render(model, form);
    }

    @PostMapping("/employees/{id}/toggle")
    String toggle(@PathVariable Long id, Principal user, RedirectAttributes flash) {
        employees.findById(id).ifPresentOrElse(e -> {
            e.setActive(!e.isActive()); employees.save(e);
            audit.log(user.getName(), e.isActive() ? "EMPLOYEE_ACTIVATE" : "EMPLOYEE_DEACTIVATE", e.getCode());
            flash.addFlashAttribute("success", e.isActive() ? "Employee activated." : "Employee deactivated.");
        }, () -> flash.addFlashAttribute("error", "Employee no longer exists."));
        return "redirect:/employees";
    }

    @PostMapping("/employees/import")
    String importCsv(@RequestParam MultipartFile file, Principal user, RedirectAttributes flash) {
        try {
            int count = employeeService.importCsv(file);
            audit.log(user.getName(), "EMPLOYEE_IMPORT", count + " rows");
            flash.addFlashAttribute("success", "Imported " + count + " employees.");
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage() + " No rows were imported.");
        } catch (Exception ex) {
            flash.addFlashAttribute("error", "Import failed. Check the CSV format and unique codes/device IDs. No rows were imported.");
        }
        return "redirect:/employees";
    }
}
