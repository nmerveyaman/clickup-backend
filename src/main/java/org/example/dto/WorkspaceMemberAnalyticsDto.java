package org.example.dto;

import java.util.List;

public record WorkspaceMemberAnalyticsDto(
        Long id,
        String username,
        String email,
        String profilePicture,
        String role,
        List<AssignedTaskDto> tasks) {

    public record AssignedTaskDto(
            String taskId,
            String taskName,
            String status,
            String activeWorkingTime,
            String taskActiveWorkingTime,
            String waitingTime,
            String totalDuration,
            String estimatedTime,
            Double estimateUsagePercentage,
            String estimateVariance,
            String estimateOutcome,
            boolean stalled,
            int rollbackCount) {}
}
