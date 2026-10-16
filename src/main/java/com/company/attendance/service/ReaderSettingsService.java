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
        if (!Set.of("PUSH", "PULL").contains(form.getConnectionMode()))
            throw new IllegalArgumentException("Choose Push or Pull connection mode.");
        if (!Set.of("ON_PREMISE", "ONLINE").contains(form.getDeploymentMode()))
            throw new IllegalArgumentException("Choose on-premise or online deployment.");
        if (form.getPollSeconds() < 15 || form.getPollSeconds() > 86400)
            throw new IllegalArgumentException("Pull interval must be between 15 and 86400 seconds.");
        if ("PULL".equals(form.getConnectionMode()) && host.isBlank())
            throw new IllegalArgumentException("Enter the reader IP or hostname for LAN pull.");
        String url = form.getServerUrl() == null ? "" : form.getServerUrl().trim().replaceAll("/+$", "");
        if (!url.isBlank()) {
            try {
                java.net.URI uri = java.net.URI.create(url);
                if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                    || !(uri.getPath().isEmpty() || uri.getPath().equals("/")) || url.length() > 255)
                    throw new IllegalArgumentException();
            } catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Enter a server base URL such as https://attendance.example.com (without a path)."); }
        }
        saved.setConnectionMode(form.getConnectionMode()); saved.setDeploymentMode(form.getDeploymentMode());
        saved.setServerUrl(url); saved.setPollSeconds(form.getPollSeconds());
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
