package org.example.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskEventResponseDto {
    private Long id;
    private String taskId;
    private String eventType;
    private String fieldName;
    private String oldValue;
    private String newValue;
    private Long changedByUserId;
    private String timestamp;
    private String idempotencyKey;
    private String historyItemId;
    private String triggerSource;
}
