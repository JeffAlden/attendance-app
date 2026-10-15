package com.company.attendance.domain;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalTime;

@Entity @Getter @Setter
public class Shift {
    @Id @GeneratedValue private Long id;
    private String name;
    private LocalTime startTime;
    private LocalTime endTime;
    private int graceMinutes = 10;
}
