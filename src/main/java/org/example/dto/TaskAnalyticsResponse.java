package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TaskAnalyticsResponse {
    private String taskId;
    private String taskName;
    private String currentStatus;
    private String priority;
    private String startDate;
    private String endDate;
    private int rollbackCount;
    private String blockedByTaskId;
    private String timeEstimate;
    private boolean scopeCreep;
    private String totalTaskDuration;
    private boolean stalled;
    private String totalActiveWorkingTime;
    private String totalWaitingTime;
    private Map<Long, String> userActiveWorkingTimes;
    private Map<Long, String> userWaitingTimes;
    private Map<String, ColumnDetail> columnDetails;
    private List<ColumnVisit> columnHistory;
    private List<String> tags;
    private int subtaskCount;
    private List<SubtaskItemDto> subtasks;
    @com.fasterxml.jackson.annotation.JsonProperty("parent_id")
    @com.fasterxml.jackson.annotation.JsonAlias({"parentId", "parent_id"})
    private String parentId;
    @com.fasterxml.jackson.annotation.JsonProperty("parent_name")
    @com.fasterxml.jackson.annotation.JsonAlias({"parentName", "parent_name"})
    private String parentName;

    @com.fasterxml.jackson.annotation.JsonProperty("parentId")
    public String getParentIdCamel() {
        return parentId;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("parentName")
    public String getParentNameCamel() {
        return parentName;
    }

    private Long assigneeId;
    private String assigneeName;
    @com.fasterxml.jackson.annotation.JsonProperty("assignee_profile_picture")
    @com.fasterxml.jackson.annotation.JsonAlias({"assigneeProfilePicture", "assignee_profile_picture"})
    private String assigneeProfilePicture;
    private List<Long> assigneeIds;

    @com.fasterxml.jackson.annotation.JsonProperty("assigneeProfilePicture")
    public String getAssigneeProfilePictureCamel() {
        return assigneeProfilePicture;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ColumnDetail {
        private String statusType;
        private String totalDurationFormatted;
        private Map<Long, String> assigneeTimes;
    }

    public record ColumnVisit(
            String statusName,
            int visitNumber,
            String statusType,
            String durationFormatted,
            Map<Long, String> assigneeTimes
    ) {}

    public record SubtaskItemDto(
            String id,
            String name,
            String statusName,
            String statusType
    ) {}
}

