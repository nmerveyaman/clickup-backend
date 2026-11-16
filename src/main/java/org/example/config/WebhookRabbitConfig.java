package org.example.config;

import java.util.Map;
import org.example.dto.WebhookIngestionPayload;
import org.example.services.WebhookEventLogService;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class WebhookRabbitConfig {
    @Bean
    SimpleRabbitListenerContainerFactory webhookListenerContainerFactory(
            ConnectionFactory connectionFactory,
            ObjectMapper objectMapper,
            WebhookEventLogService eventLogService) {
        MessageRecoverer recoverer = (message, cause) -> {
            try {
                WebhookIngestionPayload payload =
                        objectMapper.readValue(message.getBody(), WebhookIngestionPayload.class);
                eventLogService.record(payload, "FAILED", cause.getMessage());
            } catch (Exception loggingException) {
                cause.addSuppressed(loggingException);
            }
            throw new AmqpRejectAndDontRequeueException("ClickUp webhook failed after 5 attempts", cause);
        };

        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxRetries(4)
                .recoverer(recoverer)
                .build());
        return factory;
    }

    @Bean
    Queue webhookQueue(
            @Value("${app.rabbitmq.webhook-queue:clickup.webhook.events}") String queueName,
            @Value("${app.rabbitmq.webhook-dead-letter-queue:clickup.webhook.events.dlq}") String deadLetterQueueName) {
        return new Queue(
                queueName,
                true,
                false,
                false,
                Map.of(
                        "x-dead-letter-exchange",
                        "",
                        "x-dead-letter-routing-key",
                        deadLetterQueueName));
    }

    @Bean
    Queue webhookDeadLetterQueue(
            @Value("${app.rabbitmq.webhook-dead-letter-queue:clickup.webhook.events.dlq}") String queueName) {
        return new Queue(queueName, true);
    }
}
