package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SprintAnalyticsResponse {
    private String listId;
    private String listName;
    private String sprintDueDate;
    private List<StatusDto> availableColumns;
    private Long effectiveSprintStartDate;
    private int totalTasks;
    private int completedTasks;
    private String completionRatio;
    private int totalActiveAssignees;
    private int scopeCreepTaskCount;
    private List<TaskSummaryDto> scopeCreepTasks;
    private int stalledTaskCount;
    private List<TaskSummaryDto> stalledTasks;
    private int blockedTaskCount;
    private List<TaskSummaryDto> blockedTasks;
    private int totalRollbackCount;
    private String totalSprintTaskDuration;
    private String totalSprintActiveWorkingTime;
    private String totalSprintWaitingTime;
    private String bottleneckColumn;
    private Map<Long, String> userTotalActiveWorkingTimes;
    private Map<Long, String> userTotalWaitingTimes;
    private List<TaskAnalyticsResponse> taskAnalyticsList;
    private Map<Long, UserDto> usersMap;
    private Map<String, Integer> userTaskCounts;
    private Map<String, Integer> taskCountPerColumn;

    public record TaskSummaryDto(
            String id,
            String name,
            String statusName
    ) {}
}

