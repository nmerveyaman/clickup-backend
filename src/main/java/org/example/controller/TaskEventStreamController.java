package org.example.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.repository.WorkspaceMemberRepository;
import org.example.security.UserPrincipal;
import org.example.services.SseNotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RestController
@RequiredArgsConstructor
public class TaskEventStreamController {

    private final SseNotificationService sseNotificationService;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    @GetMapping(value = "/api/events/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTaskEvents(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null || principal.getWorkspaceId() == null || principal.getWorkspaceId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No workspace is selected for this account.");
        }
        String workspaceId = principal.getWorkspaceId().trim();
        if (workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, principal.getId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Workspace access denied.");
        }
        log.info("SSE event stream connection accepted for workspaceId={}", workspaceId);
        return sseNotificationService.subscribe(workspaceId);
    }
}
