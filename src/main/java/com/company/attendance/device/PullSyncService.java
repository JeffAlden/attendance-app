package com.company.attendance.device;

import com.company.attendance.domain.ReaderSettings;
import com.company.attendance.repository.ScanLogRepository;
import com.company.attendance.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import java.time.Instant;

@Service @RequiredArgsConstructor
public class PullSyncService {
    private final ReaderSettingsService settings;
    private final ZktecoPullClient client;
    private final ScanLogRepository logs;
    private final ScanService scans;
    private Instant next = Instant.EPOCH;
    private volatile String status = "No pull attempted yet.";
    public String getStatus() { return status; }

    @Scheduled(fixedDelay = 5000, initialDelay = 15000)
    public synchronized void poll() {
        ReaderSettings config = settings.current();
        if ("PULL".equals(config.getConnectionMode()) && Instant.now().isAfter(next)) {
            try { sync(); } catch (Exception ignored) { /* Status is displayed to the administrator. */ }
        }
    }
    public synchronized int sync() throws java.io.IOException {
        ReaderSettings config = settings.current();
        if (!"PULL".equals(config.getConnectionMode())) throw new IllegalArgumentException("Save LAN Pull mode before syncing.");
        next = Instant.now().plusSeconds(config.getPollSeconds());
        String device = config.getSerialNumber().isBlank() ? "PULL:" + config.getReaderHost() + ":" + config.getReaderPort() : config.getSerialNumber();
        try {
            var punches = client.read(config.getReaderHost(), config.getReaderPort());
            int count = 0;
            for (var punch : punches) {
                if (!logs.existsByDeviceIdAndRawCodeAndTimestamp(device, punch.pin(), punch.time())) {
                    scans.process(punch.pin(), device, punch.time(), "AUTO".equals(config.getDirection()) ? null
                        : com.company.attendance.domain.Direction.valueOf(config.getDirection()), "ZKTECO_PULL");
                    count++;
                }
            }
            settings.recordContact(device);
            status = "Last pull: " + java.time.LocalDateTime.now() + " — imported " + count + " new records.";
            return count;
        } catch (java.io.IOException | RuntimeException ex) {
            status = "Pull failed: " + ex.getMessage(); throw ex;
        }
    }
}
