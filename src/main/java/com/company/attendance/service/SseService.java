package com.company.attendance.service;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Pushes a "scan" event to every open dashboard; the browser then reloads its data. */
@Service
public class SseService {
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe() {
        SseEmitter e = new SseEmitter(0L);
        emitters.add(e);
        e.onCompletion(() -> emitters.remove(e));
        e.onTimeout(() -> emitters.remove(e));
        e.onError(x -> emitters.remove(e));
        return e;
    }

    public void broadcast() {
        for (SseEmitter e : emitters) {
            try { e.send(SseEmitter.event().name("scan").data("1")); }
            catch (Exception ex) { emitters.remove(e); }
        }
    }
}
