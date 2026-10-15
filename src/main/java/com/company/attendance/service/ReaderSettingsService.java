package com.company.attendance.service;

import com.company.attendance.config.AppProperties;
import com.company.attendance.domain.*;
import com.company.attendance.repository.ReaderSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Set;

@Service @RequiredArgsConstructor
public class ReaderSettingsService {
    private final ReaderSettingsRepository repository;
    private final AppProperties props;

    public ReaderSettings current() {
        return repository.findById(1L).orElseGet(() -> {
            ReaderSettings settings = new ReaderSettings();
            settings.setDebounceSeconds(props.getDebounceSeconds());
            settings.setAntiPassback(props.isAntiPassback());
            return settings;
        });
    }
    @Transactional
    public void save(ReaderSettings form) {
        if (form.getReaderName() == null || form.getReaderName().isBlank() || form.getReaderName().length() > 255)
            throw new IllegalArgumentException("Enter a reader name of up to 255 characters.");
        String host = form.getReaderHost() == null ? "" : form.getReaderHost().trim();
        if (host.length() > 253 || (!host.isBlank() && !host.matches("[A-Za-z0-9:.\\-]+")))
            throw new IllegalArgumentException("Enter a reader IP address or hostname without a URL or spaces.");
        if (form.getReaderPort() < 1 || form.getReaderPort() > 65535)
            throw new IllegalArgumentException("Reader port must be between 1 and 65535.");
        if (form.getDebounceSeconds() < 0 || form.getDebounceSeconds() > 3600)
            throw new IllegalArgumentException("Duplicate scan delay must be between 0 and 3600 seconds.");
        if (!Set.of("AUTO", "IN", "OUT").contains(form.getDirection()))
            throw new IllegalArgumentException("Choose Auto, IN, or OUT for the reader direction.");
        if (form.getSerialNumber() == null || !form.getSerialNumber().matches("[A-Za-z0-9_-]{0,100}"))
            throw new IllegalArgumentException("Reader serial may contain up to 100 letters, numbers, hyphens, or underscores.");
        ReaderSettings saved = current();
        saved.setReaderName(form.getReaderName().trim()); saved.setReaderHost(host);
        saved.setReaderPort(form.getReaderPort()); saved.setSerialNumber(form.getSerialNumber());
        saved.setDirection(form.getDirection()); saved.setEntryOnly(form.isEntryOnly());
        saved.setDebounceSeconds(form.getDebounceSeconds()); saved.setAntiPassback(form.isAntiPassback());
        repository.saveAndFlush(saved);
    }
    public Direction direction(String serial) {
        ReaderSettings settings = current();
        if (!settings.getSerialNumber().isBlank() && settings.getSerialNumber().equals(serial))
            return "AUTO".equals(settings.getDirection()) ? null : Direction.valueOf(settings.getDirection());
        String fixed = props.getDeviceDirections().get(serial);
        return fixed == null ? null : Direction.valueOf(fixed.trim().toUpperCase());
    }
    @Transactional
    public void recordContact(String serial) {
        ReaderSettings settings = current();
        settings.setLastSeen(LocalDateTime.now()); settings.setLastSeenSerial(serial);
        repository.save(settings);
    }
}
