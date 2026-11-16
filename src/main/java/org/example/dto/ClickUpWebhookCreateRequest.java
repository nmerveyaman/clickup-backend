package org.example.dto;

import java.util.Arrays;
import java.util.List;
import org.example.enums.WebhookSubscribedEvent;

public record ClickUpWebhookCreateRequest(String endpoint, List<String> events) {
    public static ClickUpWebhookCreateRequest defaultSubscription(String endpointUrl) {
        List<String> subscribedEvents = Arrays.stream(WebhookSubscribedEvent.values())
                .filter(event -> event != WebhookSubscribedEvent.UNSUPPORTED)
                .map(WebhookSubscribedEvent::getEventName)
                .toList();
        return new ClickUpWebhookCreateRequest(endpointUrl, subscribedEvents);
    }
}
