package com.company.attendance.domain;
import jakarta.persistence.*;
import lombok.*;

@Entity @Getter @Setter
public class AppUser {
    @Id @GeneratedValue private Long id;
    @Column(unique = true) private String username;
    private String passwordHash;
    @Enumerated(EnumType.STRING) private Role role;
    private boolean enabled = true;
}
