package org.example.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.Lists;
import org.example.entity.Task;
import org.example.entity.TaskAssignee;
import org.example.entity.TaskEvent;
import org.example.entity.User;
import org.example.entity.UserTokens;
import org.example.entity.WorkspaceMember;
import org.example.repository.ListRepository;
import org.example.repository.TaskAssigneeRepository;
import org.example.repository.TaskEventRepository;
import org.example.repository.TaskRepository;
import org.example.repository.UserTokensRepository;
import org.example.repository.WorkspaceMemberRepository;
import org.example.dto.TaskAnalyticsResponse;
import org.example.dto.WorkspaceMemberAnalyticsDto;
import org.example.dto.AvailableWorkspacesDto;
import org.example.services.AnalyticsService;
import org.example.services.ClickUpFullSyncService;
import org.example.security.UserPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ListRepository listRepository;
    private final TaskRepository taskRepository;
    private final TaskAssigneeRepository taskAssigneeRepository;
    private final TaskEventRepository taskEventRepository;
    private final AnalyticsService analyticsService;
    private final UserTokensRepository userTokensRepository;
    private final ClickUpFullSyncService clickUpFullSyncService;

    /**
     * The workspace selected by the authenticated token.
     * GET /api/v1/workspaces/available
     */
    @GetMapping("/available")
    @Transactional(readOnly = true)
    public ResponseEntity<AvailableWorkspacesDto> getAvailableWorkspaces(
            @AuthenticationPrincipal UserPrincipal principal) {
        java.util.Set<String> authorizedWorkspaceIds = userTokensRepository.findByUserId(principal.getId()).stream()
                .filter(token -> token.getWorkspace() != null)
                .map(token -> token.getWorkspace().getId())
                .collect(Collectors.toSet());
        List<AvailableWorkspacesDto.WorkspaceDto> workspaces = workspaceMemberRepository
                .findByUserId(principal.getId()).stream()
                .filter(member -> member.getWorkspace() != null)
                .filter(member -> authorizedWorkspaceIds.contains(member.getWorkspace().getId()))
                .map(member -> new AvailableWorkspacesDto.WorkspaceDto(
                        member.getWorkspace().getId(),
                        member.getWorkspace().getName() != null
                                ? member.getWorkspace().getName()
                                : "Workspace (" + member.getWorkspace().getId() + ")"))
                .distinct()
                .toList();
        String defaultWorkspaceId = workspaces.stream()
                .anyMatch(workspace -> workspace.id().equals(principal.getWorkspaceId()))
                ? principal.getWorkspaceId()
                : workspaces.isEmpty() ? null : workspaces.get(0).id();
        return ResponseEntity.ok(new AvailableWorkspacesDto(defaultWorkspaceId, workspaces));
    }

    /**
     * 2. Lists (sprints) in the workspace selected by the authenticated token.
     * GET /api/v1/workspaces/lists
     */
    @GetMapping("/lists")
    public ResponseEntity<List<Map<String, Object>>> getWorkspaceLists(
            @AuthenticationPrincipal UserPrincipal principal) {
        String cleanWorkspaceId = requireCurrentWorkspace(principal);
        log.info("Workspace lists requested for workspaceId={}", cleanWorkspaceId);

        List<Lists> lists = listRepository.findByWorkspaceId(cleanWorkspaceId);
        if (lists == null || lists.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<Map<String, Object>> result = lists.stream()
                .map(l -> Map.of(
                        "id", (Object) l.getId(),
                        "name", (Object) (l.getName() != null ? l.getName() : "List (" + l.getId() + ")"),
                        "taskCount", (Object) (l.getTotalTaskCount() != null ? l.getTotalTaskCount() : 0)))
                .toList();

        return ResponseEntity.ok(result);
    }

    /**
     * 3. Members of the workspace selected by the authenticated token.
     * GET /api/v1/workspaces/members
     */
    @GetMapping("/members")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> getWorkspaceMembers(
            @AuthenticationPrincipal UserPrincipal principal) {
        String workspaceId = requireCurrentWorkspace(principal);
        log.info("Workspace members requested for workspaceId={}", workspaceId);

        List<WorkspaceMember> members = workspaceMemberRepository.findByWorkspaceId(workspaceId);
        if (members == null || members.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<Map<String, Object>> result = members.stream()
                .filter(m -> m.getUser() != null)
                .map(m -> {
                    User u = m.getUser();
                    String role = u.getRole() != null ? u.getRole() : "DEVELOPER";
                    return Map.of(
                            "id", (Object) u.getId(),
                            "username",
                            (Object) (u.getUsername() != null ? u.getUsername() : "User (" + u.getId() + ")"),
                            "email", (Object) (u.getEmail() != null ? u.getEmail() : ""),
                            "profile_picture", (Object) (u.getProfilePicture() != null ? u.getProfilePicture() : ""),
                            "role_key", (Object) role,
                            "appRole", (Object) role);
                })
                .toList();

        return ResponseEntity.ok(result);
    }

    @GetMapping("/members/analytics")
    @Transactional(readOnly = true)
    public ResponseEntity<List<WorkspaceMemberAnalyticsDto>> getWorkspaceMemberAnalytics(
            @AuthenticationPrincipal UserPrincipal principal) {
        String cleanWorkspaceId = requireCurrentWorkspace(principal);
        List<WorkspaceMember> members = workspaceMemberRepository.findByWorkspaceId(cleanWorkspaceId);
        List<Task> tasks = taskRepository.findByWorkspaceId(cleanWorkspaceId);
        List<String> taskIds = tasks.stream().map(Task::getId).filter(java.util.Objects::nonNull).toList();

        Map<String, List<TaskEvent>> eventsByTaskId = taskIds.isEmpty()
                ? Collections.emptyMap()
                : taskEventRepository.findByTaskIdIn(taskIds).stream()
                .filter(event -> event.getTask() != null)
                .collect(Collectors.groupingBy(event -> event.getTask().getId()));
        Map<String, List<Long>> assigneeIdsByTaskId = new HashMap<>();
        if (!taskIds.isEmpty()) {
            for (TaskAssignee assignment : taskAssigneeRepository.findByTaskIdIn(taskIds)) {
                if (assignment.getTask() != null && assignment.getUser() != null) {
                    assigneeIdsByTaskId.computeIfAbsent(assignment.getTask().getId(), ignored -> new java.util.ArrayList<>())
                            .add(assignment.getUser().getId());
                }
            }
        }

        Map<Long, List<WorkspaceMemberAnalyticsDto.AssignedTaskDto>> tasksByUserId = new HashMap<>();
        for (Task task : tasks) {
            List<Long> assigneeIds = assigneeIdsByTaskId.getOrDefault(task.getId(), Collections.emptyList())
                    .stream()
                    .distinct()
                    .toList();
            if (assigneeIds.isEmpty()) {
                continue;
            }

            TaskAnalyticsResponse analytics = analyticsService.getAssignedTaskAnalytics(
                    task,
                    eventsByTaskId.getOrDefault(task.getId(), Collections.emptyList()),
                    assigneeIds);
            AnalyticsService.EstimateComparison estimateComparison = analyticsService.compareTimeEstimate(
                    task.getTimeEstimate(),
                    analytics.getTotalTaskDuration() == null ? -1L : analytics.getTotalActiveWorkingTimeMillis(),
                    analytics.getEndDate() != null);
            for (Long assigneeId : assigneeIds) {
                Map<Long, String> activeTimes = analytics.getUserActiveWorkingTimes();
                Map<Long, String> waitingTimes = analytics.getUserWaitingTimes();
                WorkspaceMemberAnalyticsDto.AssignedTaskDto taskDto =
                        new WorkspaceMemberAnalyticsDto.AssignedTaskDto(
                                task.getId(),
                                task.getName(),
                                analytics.getCurrentStatus(),
                                activeTimes == null ? null : activeTimes.get(assigneeId),
                                analytics.getTotalActiveWorkingTime(),
                                waitingTimes == null ? null : waitingTimes.get(assigneeId),
                                analytics.getTotalTaskDuration(),
                                estimateComparison == null ? null : estimateComparison.estimatedTime(),
                                estimateComparison == null ? null : estimateComparison.usagePercentage(),
                                estimateComparison == null ? null : estimateComparison.variance(),
                                estimateComparison == null ? null : estimateComparison.outcome(),
                                analytics.isStalled(),
                                analytics.getRollbackCount());
                tasksByUserId.computeIfAbsent(assigneeId, ignored -> new java.util.ArrayList<>()).add(taskDto);
            }
        }

        Map<Long, User> uniqueMembers = new LinkedHashMap<>();
        for (WorkspaceMember member : members) {
            if (member.getUser() != null && member.getUser().getId() != null) {
                uniqueMembers.putIfAbsent(member.getUser().getId(), member.getUser());
            }
        }

        List<WorkspaceMemberAnalyticsDto> result = uniqueMembers.values().stream()
                .map(user -> new WorkspaceMemberAnalyticsDto(
                        user.getId(),
                        user.getUsername(),
                        user.getEmail(),
                        user.getProfilePicture(),
                        user.getRole() != null ? user.getRole() : "DEVELOPER",
                        tasksByUserId.getOrDefault(user.getId(), Collections.emptyList()).stream()
                                .sorted(java.util.Comparator.comparing(
                                        WorkspaceMemberAnalyticsDto.AssignedTaskDto::taskName,
                                        java.util.Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                                .toList()))
                .toList();
        return ResponseEntity.ok(result);
    }

    /**
     * 4. Immediately synchronize the workspace selected by the authenticated token.
     * POST /api/v1/workspaces/sync
     */
    @PostMapping("/sync")
    public ResponseEntity<Map<String, Object>> triggerManualSync(
            @AuthenticationPrincipal UserPrincipal principal) {
        Long userId = principal.getId();
        String cleanWorkspaceId = requireCurrentWorkspace(principal);
        log.info("Manual workspace sync requested for workspaceId={}, userId={}", cleanWorkspaceId, userId);

        UserTokens token = userTokensRepository.findByUserIdAndWorkspaceId(userId, cleanWorkspaceId)
                .orElse(null);

        if (token == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "No valid ClickUp token was found for this workspace. Please sign in again."
            ));
        }

        try {
            clickUpFullSyncService.syncWorkspaceData(String.valueOf(userId), cleanWorkspaceId, token.getAccessToken());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Synchronization completed successfully."
            ));
        } catch (Exception e) {
            log.error("Manual workspace synchronization failed.", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Synchronization failed: " + e.getMessage()
            ));
        }
    }

    private void requireWorkspaceAccess(UserPrincipal principal, String workspaceId) {
        if (principal == null || !java.util.Objects.equals(workspaceId, principal.getWorkspaceId())
                || workspaceMemberRepository
                .findByWorkspaceIdAndUserId(workspaceId, principal.getId())
                .isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Workspace access denied");
        }
    }

    private String requireCurrentWorkspace(UserPrincipal principal) {
        if (principal == null || principal.getWorkspaceId() == null || principal.getWorkspaceId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No workspace is selected for this account");
        }
        String workspaceId = principal.getWorkspaceId().trim();
        requireWorkspaceAccess(principal, workspaceId);
        return workspaceId;
    }
}
