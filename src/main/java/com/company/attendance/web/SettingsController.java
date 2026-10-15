package com.company.attendance.web;

import com.company.attendance.domain.*;
import com.company.attendance.repository.EmployeeRepository;
import com.company.attendance.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Set;

@Controller @RequiredArgsConstructor
public class SettingsController {
    private final ReaderSettingsService settings;
    private final EmployeeRepository employees;
    private final AuditService audit;
    private final SseService sse;
    @Value("${server.port:8080}") private int serverPort;

    private String render(Model model, ReaderSettings form) {
        model.addAttribute("settings", form);
        model.addAttribute("employees", employees.findAll());
        model.addAttribute("serverPort", serverPort);
        return "settings";
    }
    @GetMapping("/settings")
    String page(Model model) { return render(model, settings.current()); }

    @PostMapping("/settings")
    String save(@ModelAttribute("settings") ReaderSettings form, BindingResult binding,
                Model model, Principal user, RedirectAttributes flash) {
        if (binding.hasErrors()) {
            model.addAttribute("error", "Enter valid whole numbers for the reader port and duplicate delay.");
            return render(model, form);
        }
        try {
            settings.save(form);
            audit.log(user.getName(), "READER_SETTINGS", form.isEntryOnly() ? "IN_ONLY" : "IN_OUT");
            sse.broadcast();
            flash.addFlashAttribute("success", "Settings saved. Attendance mode is effective immediately. Configure the reader's ADMS server separately using the instructions below.");
            return "redirect:/settings";
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
            return render(model, form);
        }
    }

    @PostMapping("/settings/enrollment")
    String enroll(@RequestParam Long employeeId, @RequestParam String finger,
                  Principal user, RedirectAttributes flash) {
        if (!Set.of("RIGHT_INDEX", "LEFT_INDEX", "RIGHT_THUMB", "LEFT_THUMB").contains(finger)) {
            flash.addFlashAttribute("error", "Select a finger for the demo enrollment.");
            return "redirect:/settings";
        }
        employees.findById(employeeId).ifPresentOrElse(employee -> {
            if (!employee.isActive()) { flash.addFlashAttribute("error", "Activate this employee before demo enrollment."); return; }
            employee.setDummyEnrolledAt(LocalDateTime.now()); employee.setDummyFinger(finger);
            employees.save(employee);
            audit.log(user.getName(), "DUMMY_ENROLLMENT", employee.getCode() + " " + finger);
            flash.addFlashAttribute("success", "Demo enrollment saved for " + employee.getFullName() + ". No fingerprint was captured or sent to a reader.");
        }, () -> flash.addFlashAttribute("error", "Employee no longer exists."));
        return "redirect:/settings";
    }
    @PostMapping("/settings/enrollment/{id}/clear")
    String clear(@PathVariable Long id, Principal user, RedirectAttributes flash) {
        employees.findById(id).ifPresentOrElse(employee -> {
            employee.setDummyEnrolledAt(null); employee.setDummyFinger(null); employees.save(employee);
            audit.log(user.getName(), "DUMMY_ENROLLMENT_CLEAR", employee.getCode());
            flash.addFlashAttribute("success", "Demo enrollment cleared.");
        }, () -> flash.addFlashAttribute("error", "Employee no longer exists."));
        return "redirect:/settings";
    }
}
