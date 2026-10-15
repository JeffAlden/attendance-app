package com.company.attendance.service;

import com.company.attendance.config.AppProperties;
import com.company.attendance.domain.*;
import com.company.attendance.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import java.time.*;
import java.util.Optional;

/** Single entry point for every scan (ZKTeco push, REST API, simulator). */
@Service @RequiredArgsConstructor
public class ScanService {
    private final EmployeeRepository employees;
    private final ScanLogRepository logs;
    private final SseService sse;
    private final AppProperties props;

    @Transactional
    public ScanLog process(String code, String deviceId, LocalDateTime ts, Direction requested, String source) {
        ScanLog l = new ScanLog();
        l.setRawCode(code); l.setDeviceId(deviceId); l.setSource(source);
        l.setTimestamp(ts != null ? ts : LocalDateTime.now());

        Employee e = employees.findByCodeOrBiometricId(code, code).filter(Employee::isActive).orElse(null);
        if (e == null) { l.setStatus(ScanStatus.DENIED); return finish(l); }
        l.setEmployee(e);

        Optional<ScanLog> last = logs.findFirstByEmployeeAndStatusOrderByTimestampDesc(e, ScanStatus.OK);
        if (last.isPresent() && Math.abs(Duration.between(last.get().getTimestamp(), l.getTimestamp()).toSeconds()) < props.getDebounceSeconds()) {
            l.setStatus(ScanStatus.DUPLICATE); l.setDirection(last.get().getDirection());
            return finish(l);
        }
        // Reader's direction wins; otherwise toggle from the last accepted scan.
        Direction d = requested != null ? requested
            : last.map(x -> x.getDirection() == Direction.IN ? Direction.OUT : Direction.IN).orElse(Direction.IN);
        l.setDirection(d);

        boolean alreadyIn = last.map(x -> x.getDirection() == Direction.IN).orElse(false);
        if (props.isAntiPassback() && d == Direction.IN && alreadyIn) { l.setStatus(ScanStatus.PASSBACK_BLOCKED); return finish(l); }
        l.setStatus(ScanStatus.OK);
        return finish(l);
    }

    private ScanLog finish(ScanLog l) {
        ScanLog saved = logs.save(l);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { sse.broadcast(); }
            });
        } else sse.broadcast();
        return saved;
    }
}
