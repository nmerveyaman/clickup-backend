package org.example.services;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.WebhookIngestionPayload;
import org.example.entity.Webhook;
import org.example.repository.WebhookRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookIngestionService {
    private final ObjectMapper objectMapper;
    private final WebhookRepository webhookRepository;
    private final WebhookSignatureService signatureService;
    private final RabbitTemplate rabbitTemplate;

    @Value("${app.rabbitmq.webhook-queue:clickup.webhook.events}")
    private String queueName;

    @Value("${clickup.webhook.secret:}")
    private String envWebhookSecret;

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
                ? "unknown"
                : webhookIdNode.isTextual() ? webhookIdNode.textValue() : webhookIdNode.toString();

        log.info("ClickUp Webhook alındı. Webhook ID: {}, Signature: {}", webhookId, signature);

        // 1. Secret doğrulaması: Önce DB'ye bak, yoksa ENV'deki CLICKUP_WEBHOOK_SECRET'a bak
        String secretToUse = null;
        if (!"unknown".equals(webhookId)) {
            secretToUse = webhookRepository.findById(webhookId)
                    .map(Webhook::getSecret)
                    .filter(s -> !s.isBlank())
                    .orElse(null);
        }

        if (secretToUse == null && envWebhookSecret != null && !envWebhookSecret.isBlank()) {
            secretToUse = envWebhookSecret;
        }

        // Eğer secret tanımlıysa imza kontrolü yap
        if (secretToUse != null && !secretToUse.isBlank() && signature != null && !signature.isBlank()) {
            if (!signatureService.verifySignature(rawPayload, signature, secretToUse)) {
                log.warn("Webhook imza doğrulaması başarısız! Webhook ID: {}", webhookId);
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature");
            }
            log.info("Webhook imza doğrulaması başarılı.");
        } else {
            log.info("Webhook secret veya imza bulunamadı; esnek modda kuyruğa iletiliyor.");
        }

        // 2. Mesajı RabbitMQ kuyruğuna fırlat
        WebhookIngestionPayload message = WebhookIngestionPayload.builder()
                .rawJsonPayload(rawPayload)
                .deliveryId(UUID.randomUUID().toString())
                .webhookId(webhookId)
                .build();
        try {
            rabbitTemplate.convertAndSend(queueName, objectMapper.writeValueAsString(message));
            log.info("Webhook mesajı RabbitMQ kuyruğuna ({}) başarıyla gönderildi.", queueName);
        } catch (JacksonException e) {
            log.error("Webhook mesajı encode edilemedi: ", e);
            throw new IllegalStateException("Could not encode webhook message", e);
        }
    }
}
