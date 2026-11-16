package org.example.services;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.example.dto.WebhookIngestionPayload;
import org.example.repository.WebhookRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class WebhookIngestionService {
    private final ObjectMapper objectMapper;
    private final WebhookRepository webhookRepository;
    private final WebhookSignatureService signatureService;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.webhook-queue:clickup.webhook.events}")
    private String queueName;

    public void accept(String rawPayload, String signature) {
        if (rawPayload == null || rawPayload.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook payload must not be empty");
        }

        JsonNode payload;
        try {
            payload = objectMapper.readTree(rawPayload);
        } catch (JacksonException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid webhook JSON", e);
        }

        JsonNode webhookIdNode = payload.get("webhook_id");
        String webhookId = webhookIdNode == null || webhookIdNode.isNull()
                ? null
                : webhookIdNode.isTextual() ? webhookIdNode.textValue() : webhookIdNode.toString();
        if (webhookId == null || webhookId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook payload has no webhook_id");
        }

        var webhook = webhookRepository.findById(webhookId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown webhook"));

        if (webhook.getSecret() == null
                || webhook.getSecret().isBlank()
                || !signatureService.verifySignature(rawPayload, signature, webhook.getSecret())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature");
        }

        WebhookIngestionPayload message = WebhookIngestionPayload.builder()
                .rawJsonPayload(rawPayload)
                .deliveryId(UUID.randomUUID().toString())
                .webhookId(webhookId)
                .build();
        try {
            rabbitTemplate.convertAndSend(queueName, objectMapper.writeValueAsString(message));
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not encode webhook message", e);
        }
    }
}
