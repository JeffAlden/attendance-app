package com.company.attendance.web;
import com.company.attendance.service.ReaderSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@ControllerAdvice @RequiredArgsConstructor
public class SettingsViewAdvice {
    private final ReaderSettingsService settings;
    @ModelAttribute("entryOnly") public boolean entryOnly() { return settings.current().isEntryOnly(); }
}
