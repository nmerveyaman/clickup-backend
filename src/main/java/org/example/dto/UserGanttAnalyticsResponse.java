package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserGanttAnalyticsResponse {
    private Long userId;
    private String listId;
    private long netTotalActiveMillis;
    private String netTotalActiveWorkingTime;
    private String rawTotalTaskWorkingTime;
    private int totalAssignedTasksCount;
    private List<UserTaskGanttDto> taskTimelines;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserTaskGanttDto {
        private String taskId;
        private String taskName;
        private String statusName;
        private String durationFormatted;
        private long firstStartedAt;
        private long lastEndedAt;
        private List<WorkIntervalDto> intervals;
    }

    public record WorkIntervalDto(
            String statusName,
            long startTimestamp,
            long endTimestamp,
            String durationFormatted,
            long durationMillis
    ) {}
}

