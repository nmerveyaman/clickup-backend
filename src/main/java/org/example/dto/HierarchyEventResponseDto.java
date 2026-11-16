package org.example.dto;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HierarchyEventResponseDto {
    private Long id;
    private String entityType;
    private String entityId;
    private String eventType;
    private String fieldName;
    private String oldValue;
    private String newValue;
    private Long changedByUserId;
    private String idempotencyKey;
    private String triggerSource;
    private OffsetDateTime createdAt;
}
