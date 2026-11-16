package org.example.services;

import lombok.RequiredArgsConstructor;
import org.example.dto.WebhookIngestionPayload;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class WebhookEventListener {
    private final ObjectMapper objectMapper;
    private final WebhookEventProcessor eventProcessor;

    @RabbitListener(
            queues = "${app.rabbitmq.webhook-queue:clickup.webhook.events}",
            containerFactory = "webhookListenerContainerFactory")
    public void handle(String message) {
        try {
            WebhookIngestionPayload payload = objectMapper.readValue(message, WebhookIngestionPayload.class);
            eventProcessor.process(payload);
        } catch (Exception e) {
            throw new IllegalStateException("ClickUp webhook processing failed", e);
        }
    }
}
