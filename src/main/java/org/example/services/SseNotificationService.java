package org.example.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class SseNotificationService {

    // workspaceId -> SseEmitter listesi
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String workspaceId) {
        // 30 dakikalık timeout
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

        emitters.computeIfAbsent(workspaceId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(workspaceId, emitter));
        emitter.onTimeout(() -> removeEmitter(workspaceId, emitter));
        emitter.onError(e -> removeEmitter(workspaceId, emitter));

        try {
            // Frontend'in eventSource.addEventListener('connected', ...) beklemesi için
            emitter.send(SseEmitter.event().name("connected").data("connected"));
            log.info("SSE istemcisi bağlandı: workspaceId={}", workspaceId);
        } catch (IOException e) {
            log.warn("İlk SSE connected mesajı gönderilemedi: workspaceId={}", workspaceId);
            removeEmitter(workspaceId, emitter);
        }

        return emitter;
    }

    public void broadcastTaskEvent(String workspaceId, Object taskEventPayload) {
        List<SseEmitter> list = emitters.get(workspaceId);
        if (list == null || list.isEmpty()) {
            return;
        }

        log.info("Workspace {} için {} aboneye task-event yayını yapılıyor...", workspaceId, list.size());
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("task-event").data(taskEventPayload));
            } catch (Exception e) {
                removeEmitter(workspaceId, emitter);
            }
        }
    }

    public void broadcastHierarchyEvent(String workspaceId, Object hierarchyEventPayload) {
        List<SseEmitter> list = emitters.get(workspaceId);
        if (list == null || list.isEmpty()) {
            return;
        }

        log.info("Workspace {} için {} aboneye hierarchy-event yayını yapılıyor...", workspaceId, list.size());
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name("hierarchy-event").data(hierarchyEventPayload));
            } catch (Exception e) {
                removeEmitter(workspaceId, emitter);
            }
        }
    }

    private void removeEmitter(String workspaceId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(workspaceId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(workspaceId);
            }
        }
    }

    @Scheduled(fixedRate = 25000)
    public void sendHeartbeat() {
        emitters.forEach((workspaceId, list) -> {
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(SseEmitter.event().comment("keepalive"));
                } catch (Exception e) {
                    removeEmitter(workspaceId, emitter);
                }
            }
        });
    }
}