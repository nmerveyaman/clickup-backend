package org.example.services;

import java.sql.Timestamp;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.example.dto.WebhookIngestionPayload;
import org.example.entity.WebhookEventLogs;
import org.example.repository.WebhookEventLogRepository;
import org.example.repository.WebhookRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class WebhookEventLogService {
    private final WebhookEventLogRepository eventLogRepository;
    private final WebhookRepository webhookRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void record(WebhookIngestionPayload payload, String status, String errorMessage) {
        JsonNode body;
        try {
            body = objectMapper.readTree(payload.getRawJsonPayload());
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Could not read webhook event log metadata", e);
        }
        String eventType = text(body.get("event"));
        String entityId = firstText(body, "task_id", "list_id", "folder_id", "space_id");

        WebhookEventLogs log = new WebhookEventLogs();
        log.setWebhook(webhookRepository.findById(payload.getWebhookId()).orElse(null));
        log.setWorkspace(log.getWebhook() == null ? null : log.getWebhook().getWorkspace());
        log.setEventType(eventType);
        log.setEntityId(entityId);
        log.setStatus(status);
        log.setErrorMessage(errorMessage == null || errorMessage.length() <= 255
                ? errorMessage
                : errorMessage.substring(0, 255));
        log.setRetryCount(0);
        log.setReceivedAt(Timestamp.from(Instant.now()));
        if ("PROCESSED".equals(status)) {
            log.setProcessedAt(Timestamp.from(Instant.now()));
        }
        eventLogRepository.save(log);
    }

    private String firstText(JsonNode node, String... names) {
        for (String name : names) {
            String value = text(node.get(name));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.isTextual() ? node.textValue() : node.toString();
    }
}
