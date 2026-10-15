package com.company.attendance.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Getter @Setter
public class ReaderSettings {
    @Id private Long id = 1L;
    private boolean entryOnly = false;
    private String readerName = "Main entrance";
    private String readerHost = "";
    private int readerPort = 4370;
    private String serialNumber = "";
    private String direction = "AUTO";
    private int debounceSeconds = 10;
    private boolean antiPassback = false;
    private LocalDateTime lastSeen;
    private String lastSeenSerial;
}
