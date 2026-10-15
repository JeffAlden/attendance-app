package com.company.attendance.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.util.*;

/** Settings from application.yml (prefix "attendance"). */
@Getter @Setter
@ConfigurationProperties(prefix = "attendance")
public class AppProperties {
    private String dataDir;
    private int debounceSeconds = 10;
    private boolean antiPassback = false;
    private String timezone = "";
    private Map<String, String> deviceDirections = new HashMap<>();

    @PostConstruct
    void applyTimezone() {
        if (timezone != null && !timezone.isBlank()) TimeZone.setDefault(TimeZone.getTimeZone(timezone));
    }
}
