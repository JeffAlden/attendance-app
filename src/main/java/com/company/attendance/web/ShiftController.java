package com.company.attendance.web;

import com.company.attendance.domain.Shift;
import com.company.attendance.repository.ShiftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.time.LocalTime;

@Controller @RequiredArgsConstructor
public class ShiftController {
    private final ShiftRepository shifts;

    @GetMapping("/shifts") String list(Model m) { m.addAttribute("shifts", shifts.findAll()); return "shifts"; }

    @PostMapping("/shifts")
    String save(@RequestParam String name,
                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime start,
                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime end,
                @RequestParam(defaultValue = "10") int grace) {
        Shift s = new Shift(); s.setName(name); s.setStartTime(start); s.setEndTime(end); s.setGraceMinutes(grace);
        shifts.save(s);
        return "redirect:/shifts";
    }
}
