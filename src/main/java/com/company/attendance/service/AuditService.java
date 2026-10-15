package com.company.attendance.service;
import com.company.attendance.domain.AuditLog;
import com.company.attendance.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service @RequiredArgsConstructor
public class AuditService {
    private final AuditLogRepository repo;
    public void log(String user, String action, String details) {
        AuditLog a = new AuditLog();
        a.setUsername(user); a.setAction(action); a.setDetails(details);
        repo.save(a);
    }
}
