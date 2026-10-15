package com.company.attendance.web;

import com.company.attendance.domain.Shift;
import com.company.attendance.repository.*;
import com.company.attendance.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.security.Principal;

@Controller @RequiredArgsConstructor
public class ShiftController {
    private final ShiftRepository shifts;
    private final EmployeeRepository employees;
    private final AuditService audit;

    private String render(Model model, Shift shift) {
        model.addAttribute("shifts", shifts.findAll());
        model.addAttribute("shift", shift);
        model.addAttribute("assignedShiftIds", employees.findAll().stream()
            .filter(e -> e.getShift() != null).map(e -> e.getShift().getId()).distinct().toList());
        return "shifts";
    }

    @GetMapping("/shifts")
    String list(@RequestParam(required = false) Long edit, Model model) {
        Shift shift = edit == null ? new Shift() : shifts.findById(edit).orElse(null);
        if (shift == null) { model.addAttribute("error", "Shift no longer exists."); shift = new Shift(); }
        return render(model, shift);
    }

    @PostMapping("/shifts")
    String save(@RequestParam(required = false) Long id, @RequestParam String name,
                @RequestParam String start, @RequestParam String end,
                @RequestParam(defaultValue = "10") String grace,
                Model model, Principal user, RedirectAttributes flash) {
        Shift form = new Shift(); form.setId(id); form.setName(name.trim());
        model.addAttribute("submittedStart", start); model.addAttribute("submittedEnd", end);
        model.addAttribute("submittedGrace", grace);
        try {
            if (name.isBlank() || name.trim().length() > 255) throw new IllegalArgumentException("Enter a shift name of 1 to 255 characters.");
            form.setStartTime(LocalTime.parse(start)); form.setEndTime(LocalTime.parse(end));
            form.setGraceMinutes(Integer.parseInt(grace));
            if (form.getStartTime().equals(form.getEndTime())) throw new IllegalArgumentException("Start and end times must be different.");
            if (form.getGraceMinutes() < 0 || form.getGraceMinutes() > 1440) throw new IllegalArgumentException("Grace must be between 0 and 1440 minutes.");
            if (shifts.findAll().stream().anyMatch(s -> !java.util.Objects.equals(s.getId(), id) && s.getName().equalsIgnoreCase(name.trim())))
                throw new IllegalArgumentException("A shift with this name already exists.");
            Shift saved = id == null ? new Shift() : shifts.findById(id).orElseThrow(() -> new IllegalArgumentException("Shift no longer exists."));
            saved.setName(form.getName()); saved.setStartTime(form.getStartTime());
            saved.setEndTime(form.getEndTime()); saved.setGraceMinutes(form.getGraceMinutes());
            shifts.saveAndFlush(saved);
            audit.log(user.getName(), id == null ? "SHIFT_ADD" : "SHIFT_EDIT", saved.getName());
            flash.addFlashAttribute("success", id == null ? "Shift added." : "Shift updated.");
            return "redirect:/shifts";
        } catch (DateTimeParseException | NumberFormatException ex) {
            model.addAttribute("error", "Enter valid start/end times and a whole number for grace minutes.");
        } catch (IllegalArgumentException ex) {
            model.addAttribute("error", ex.getMessage());
        }
        return render(model, form);
    }

    @PostMapping("/shifts/{id}/delete")
    String delete(@PathVariable Long id, Principal user, RedirectAttributes flash) {
        if (employees.existsByShiftId(id)) {
            flash.addFlashAttribute("error", "Reassign employees using this shift before deleting it.");
        } else {
            shifts.findById(id).ifPresentOrElse(s -> {
                try {
                    shifts.deleteById(id);
                    audit.log(user.getName(), "SHIFT_DELETE", s.getName());
                    flash.addFlashAttribute("success", "Shift deleted.");
                } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                    flash.addFlashAttribute("error", "This shift is now assigned to an employee and cannot be deleted.");
                }
            }, () -> flash.addFlashAttribute("error", "Shift no longer exists."));
        }
        return "redirect:/shifts";
    }
}
