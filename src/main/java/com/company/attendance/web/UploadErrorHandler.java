package com.company.attendance.web;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@ControllerAdvice
public class UploadErrorHandler {
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String tooLarge(RedirectAttributes flash) {
        flash.addFlashAttribute("error", "Upload is too large. Select a file of 5 MB or smaller and submit again.");
        return "redirect:/employees";
    }
}
