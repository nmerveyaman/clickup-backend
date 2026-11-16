package org.example.services;

import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.example.entity.HierarchyEvent;
import org.example.entity.Task;
import org.example.entity.TaskEvent;
import org.example.repository.HierarchyEventRepository;
import org.example.repository.TaskEventRepository;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class SyncEventRecorder {
    private final HierarchyEventRepository hierarchyEventRepository;
    private final TaskEventRepository taskEventRepository;
    private final ObjectMapper objectMapper;

    public void recordHierarchyChange(String entityType, String entityId, String field, Object before, Object after) {
        if (Objects.deepEquals(before, after)) {
            return;
        }

        hierarchyEventRepository.save(HierarchyEvent.builder()
                .entityType(entityType)
                .entityId(entityId)
                .eventType("SYNC_UPDATE")
                .fieldName(field)
                .oldValue(toJson(before))
                .newValue(toJson(after))
                .idempotencyKey(UUID.randomUUID().toString())
                .triggerSource("SYNC")
                .build());
    }

    public void recordTaskChange(Task task, String fieldName, Object before, Object after) {
        if (Objects.deepEquals(before, after)) {
            return;
        }

        TaskEvent event = new TaskEvent();
        event.setTask(task);
        event.setEventType(taskEventType(fieldName));
        event.setFieldName(fieldName);
        event.setOldValue(toJson(before));
        event.setNewValue(toJson(after));
        event.setTimestamp(Long.toString(System.currentTimeMillis()));
        event.setIdempotencyKey(UUID.randomUUID().toString());
        event.setTriggerSource("SYNC");
        taskEventRepository.save(event);
    }

    private String taskEventType(String fieldName) {
        return switch (fieldName) {
            case "status" -> "taskStatusUpdated";
            case "priority" -> "taskPriorityUpdated";
            case "assignee" -> "taskAssigneeUpdated";
            default -> "taskUpdated";
        };
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof CharSequence || value instanceof Number || value instanceof Boolean || value instanceof Enum<?>) {
            return value.toString();
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not serialize synchronized value for event history", e);
        }
    }
}
