package com.company.attendance.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Getter @Setter
public class ReaderSettings {
    @Id private Long id = 1L;
    private boolean entryOnly = false;
    @Column(columnDefinition = "varchar(255) default 'PUSH'")
    private String connectionMode = "PUSH";
    @Column(columnDefinition = "varchar(255) default 'ON_PREMISE'")
    private String deploymentMode = "ON_PREMISE";
    @Column(columnDefinition = "varchar(255) default ''")
    private String serverUrl = "";
    @Column(columnDefinition = "integer default 60")
    private int pollSeconds = 60;
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
