package com.company.attendance.web;

import com.company.attendance.repository.EmployeeRepository;
import com.company.attendance.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.Map;

@Controller @RequiredArgsConstructor
public class DashboardController {
    private final DashboardService dashboard;
    private final SseService sse;
    private final EmployeeRepository employees;

    @GetMapping("/login") String login() { return "login"; }
    @GetMapping("/") String home() { return "redirect:/dashboard"; }
    @GetMapping("/dashboard") String page() { return "dashboard"; }
    @GetMapping("/dashboard/data") @ResponseBody Map<String, Object> data() { return dashboard.data(); }
    @GetMapping("/dashboard/stream") @ResponseBody SseEmitter stream() { return sse.subscribe(); }

    @GetMapping("/simulator") String simulator(Model m) { m.addAttribute("employees", employees.findAll()); return "simulator"; }
}
