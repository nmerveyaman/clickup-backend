package org.example.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.services.SseNotificationService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequiredArgsConstructor
public class TaskEventStreamController {

    private final SseNotificationService sseNotificationService;


    @GetMapping(value = "/api/events/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTaskEvents(@RequestParam(name = "workspaceId", defaultValue = "default") String workspaceId) {
        log.info("SSE event stream bağlantı isteği alındı: workspaceId={}", workspaceId);
        return sseNotificationService.subscribe(workspaceId);
    }
}

