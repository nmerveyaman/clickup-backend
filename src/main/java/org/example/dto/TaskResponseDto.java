package org.example.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record TaskResponseDto(@JsonProperty("tasks") List<TaskDto> tasks) {
    public record TaskDto(
            String id,
            String name,
            StatusDto status,
            @JsonProperty("date_created") String dateCreated,
            @JsonProperty("date_updated") String dateUpdated,
            @JsonProperty("date_closed") String dateClosed,
            Boolean archived,
            @JsonProperty("time_estimate") String timeEstimate,
            String parent,
            @JsonProperty("top_level_parent") String topLevelParent,
            PriorityDto priority,
            UserDto creator,
            List<UserDto> assignees,
            List<DependencyDto> dependencies,
            String url,
            LocationDto list,
            LocationDto folder,
            LocationDto space,
            @JsonProperty("team_id") String teamId,
            List<TagDto> tags,
            List<TaskDto> subtasks) {}

    public record TagDto(String name, @JsonProperty("tag_fg") String tagFg, @JsonProperty("tag_bg") String tagBg) {}

    public record PriorityDto(String id, String priority) {}

    public record DependencyDto(
            @JsonProperty("task_id") String taskId,
            @JsonProperty("depends_on") String dependsOn,
            @JsonProperty("user_id") String userId,
            @JsonProperty("date_created") Long dateCreated) {}

    public record LocationDto(String id, String name) {}
}
