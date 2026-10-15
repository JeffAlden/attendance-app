package com.company.attendance.domain;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Getter @Setter
public class AuditLog {
    @Id @GeneratedValue private Long id;
    private String username;
    private String action;
    @Column(length = 500) private String details;
    private LocalDateTime timestamp = LocalDateTime.now();
}
