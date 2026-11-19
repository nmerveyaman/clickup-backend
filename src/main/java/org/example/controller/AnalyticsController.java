package org.example.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.SprintAnalyticsResponse;
import org.example.dto.TaskAnalyticsResponse;
import org.example.dto.UserGanttAnalyticsResponse;
import org.example.entity.Lists;
import org.example.entity.Task;
import org.example.entity.TaskEvent;
import org.example.repository.ListRepository;
import org.example.repository.TaskEventRepository;
import org.example.repository.TaskRepository;
import org.example.repository.WorkspaceMemberRepository;
import org.example.security.UserPrincipal;
import org.example.services.AnalyticsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final TaskRepository taskRepository;
    private final TaskEventRepository taskEventRepository;
    private final ListRepository listRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;

    /**
     * 1. Tekil Görev (Task) Kapsamlı Analiz Endpoint'i
     */
    @GetMapping("/task/{taskId}")
    public ResponseEntity<TaskAnalyticsResponse> getTaskAnalytics(
            @PathVariable String taskId,
            @AuthenticationPrincipal UserPrincipal principal) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task bulunamadı: " + taskId));
        requireTaskWorkspaceAccess(task, principal);

        List<TaskEvent> events = taskEventRepository.findByTaskIdOrderByTimestampAsc(taskId);

        Lists listEntity = task.getList();

        TaskAnalyticsResponse response = analyticsService.getComprehensiveAnalytics(task, events, listEntity);
        return ResponseEntity.ok(response);
    }

    /**
     * 2. Sprint (List) Bazlı Toplu Özet Analiz Endpoint'i
     */
    @GetMapping("/sprint/{listId}")
    public ResponseEntity<SprintAnalyticsResponse> getSprintAnalytics(
            @PathVariable String listId,
            @AuthenticationPrincipal UserPrincipal principal) {
        String cleanListId = (listId != null) ? listId.trim() : "";
        log.info("Sprint analizi isteniyor. Liste ID: '{}'", cleanListId);

        Lists listEntity = listRepository.findById(cleanListId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Sprint / Liste veritabanında bulunamadı: '" + cleanListId + "'"
                ));
        requireWorkspaceAccess(listEntity.getSpace().getWorkspace().getId(), principal);

        List<Task> tasks = taskRepository.findByListId(cleanListId);
        log.info("Liste ID '{}' için veritabanında {} adet görev bulundu.", cleanListId, tasks.size());

        SprintAnalyticsResponse response = analyticsService.getSprintComprehensiveAnalytics(listEntity, tasks);
        return ResponseEntity.ok(response);
    }

    /**
     * 3. Kullanıcı (Assignee) Bazlı Gantt Chart ve Net Çalışma Süresi Endpoint'i
     * (Belirli bir sprint için ?listId=... gönderilebilir; gönderilmezse kullanıcının tüm görevlerini tarar)
     */
    @GetMapping("/user/gantt")
    public ResponseEntity<UserGanttAnalyticsResponse> getUserGanttAnalytics(
            @RequestParam(required = false) String listId,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        Long userId = principal.getId();

        List<Task> tasks;
        if (listId != null && !listId.isBlank()) {
            Lists list = listRepository.findById(listId.trim())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "List not found"));
            String workspaceId = list.getSpace().getWorkspace().getId();
            requireWorkspaceAccess(workspaceId, principal);
            requireWorkspaceMember(userId, workspaceId);
            tasks = taskRepository.findByListId(listId.trim());
        } else {
            String workspaceId = principal.getWorkspaceId();
            if (workspaceId == null || workspaceId.isBlank()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No workspace is selected for this account");
            }
            requireWorkspaceAccess(workspaceId, principal);
            tasks = taskRepository.findByWorkspaceId(workspaceId);
        }

        UserGanttAnalyticsResponse response = analyticsService.getUserGanttAnalytics(userId, listId, tasks);
        return ResponseEntity.ok(response);
    }

    private void requireTaskWorkspaceAccess(Task task, UserPrincipal principal) {
        if (task.getList() == null || task.getList().getSpace() == null
                || task.getList().getSpace().getWorkspace() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Task is not linked to an accessible workspace");
        }
        requireWorkspaceAccess(task.getList().getSpace().getWorkspace().getId(), principal);
    }

    private void requireWorkspaceAccess(String workspaceId, UserPrincipal principal) {
        if (principal == null || !java.util.Objects.equals(workspaceId, principal.getWorkspaceId())
                || workspaceMemberRepository
                .findByWorkspaceIdAndUserId(workspaceId, principal.getId())
                .isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Workspace access denied");
        }
    }

    private void requireWorkspaceMember(Long userId, String workspaceId) {
        if (!isWorkspaceMember(userId, workspaceId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is not a member of this workspace");
        }
    }

    private boolean isWorkspaceMember(Long userId, String workspaceId) {
        return workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, userId).isPresent();
    }
}
