package org.example.dto;

import java.util.List;

public record AvailableWorkspacesDto(String defaultWorkspaceId, List<WorkspaceDto> workspaces) {
    public record WorkspaceDto(String id, String name) {}
}
