package com.company.attendance.domain;
import jakarta.persistence.*;
import lombok.*;

@Entity @Getter @Setter
public class Employee {
    @Id @GeneratedValue private Long id;
    @Column(unique = true) private String code;
    private String fullName;
    private String department;
    private String photoPath;
    private String biometricId;   // the User ID / PIN enrolled on the ZKTeco device
    @ManyToOne private Shift shift;
    private boolean active = true;
}
