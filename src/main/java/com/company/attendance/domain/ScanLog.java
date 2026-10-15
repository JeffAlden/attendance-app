package com.company.attendance.domain;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Getter @Setter
@Table(indexes = @Index(columnList = "timestamp"))
public class ScanLog {
    @Id @GeneratedValue private Long id;
    @ManyToOne private Employee employee;      // null when the code is unknown
    private String rawCode;
    private String deviceId;
    private LocalDateTime timestamp;           // device time is preserved
    @Enumerated(EnumType.STRING) private Direction direction;
    @Enumerated(EnumType.STRING) private ScanStatus status;
    private String source;                     // ZKTECO / API / SIMULATOR
}
