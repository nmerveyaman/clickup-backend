package org.example.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.Lists;
import org.example.entity.User;
import org.example.entity.WorkspaceMember;
import org.example.repository.ListRepository;
import org.example.repository.WorkspaceMemberRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {

    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final ListRepository listRepository;

    /**
     * 1. Kullanıcının erişebildiği Workspace listesi
     * GET /api/v1/workspaces/available?userId=12345
     */
    @GetMapping("/available")
    public ResponseEntity<List<Map<String, String>>> getAvailableWorkspaces(@RequestParam Long userId) {
        log.info("Kullanıcı için erişilebilir workspace'ler isteniyor: userId={}", userId);

        List<WorkspaceMember> memberships = workspaceMemberRepository.findByUserId(userId);
        if (memberships == null || memberships.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<Map<String, String>> result = memberships.stream()
                .filter(m -> m.getWorkspace() != null)
                .map(m -> Map.of(
                        "id", m.getWorkspace().getId(),
                        "name",
                        m.getWorkspace().getName() != null ? m.getWorkspace().getName()
                                : "Workspace (" + m.getWorkspace().getId() + ")"))
                .distinct()
                .toList();

        return ResponseEntity.ok(result);
    }

    /**
     * 2. Belirtilen Workspace altındaki tüm Listeler (Sprint'ler)
     * GET /api/v1/workspaces/lists?workspaceId=90123456
     */
    @GetMapping("/lists")
    public ResponseEntity<List<Map<String, Object>>> getWorkspaceLists(@RequestParam String workspaceId) {
        log.info("Workspace listeleri isteniyor: workspaceId={}", workspaceId);

        List<Lists> lists = listRepository.findByWorkspaceId(workspaceId.trim());
        if (lists == null || lists.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<Map<String, Object>> result = lists.stream()
                .map(l -> Map.of(
                        "id", (Object) l.getId(),
                        "name", (Object) (l.getName() != null ? l.getName() : "Liste (" + l.getId() + ")"),
                        "taskCount", (Object) (l.getTotalTaskCount() != null ? l.getTotalTaskCount() : 0)))
                .toList();

        return ResponseEntity.ok(result);
    }

    /**
     * 3. Belirtilen Workspace'e ait üyeler
     * GET /api/v1/workspaces/{workspaceId}/members
     */
    @GetMapping("/{workspaceId}/members")
    public ResponseEntity<List<Map<String, Object>>> getWorkspaceMembers(@PathVariable String workspaceId) {
        log.info("Workspace üyeleri isteniyor: workspaceId={}", workspaceId);

        List<WorkspaceMember> members = workspaceMemberRepository.findByWorkspaceId(workspaceId.trim());
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
                            (Object) (u.getUsername() != null ? u.getUsername() : "Kullanıcı (" + u.getId() + ")"),
                            "email", (Object) (u.getEmail() != null ? u.getEmail() : ""),
                            "profile_picture", (Object) (u.getProfilePicture() != null ? u.getProfilePicture() : ""),
                            "role_key", (Object) role,
                            "appRole", (Object) role);
                })
                .toList();

        return ResponseEntity.ok(result);
    }
}
