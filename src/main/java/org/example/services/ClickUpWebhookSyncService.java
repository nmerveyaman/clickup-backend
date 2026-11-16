package org.example.services;

import java.sql.Timestamp;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.clickup.api.WebhookResponse;
import org.example.clickup.webhook.Webhook;
import org.example.clickup.webhook.WebhookEventType;
import org.example.client.ClickupClient;
import org.example.config.ClickUpWebhookProperties;
import org.example.dto.ClickUpWebhookCreateRequest;
import org.example.entity.UserTokens;
import org.example.repository.WebhookRepository;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClickUpWebhookSyncService {
    private final ClickupClient clickupClient;
    private final ClickUpWebhookProperties properties;
    private final WebhookRepository webhookRepository;

    public void syncWorkspaceWebhook(UserTokens token) {
        if (token.getWorkspace() == null || token.getUser() == null) {
            throw new IllegalStateException("Webhook sync token must have a user and workspace");
        }
        String workspaceId = token.getWorkspace().getId();
        String accessToken = token.getAccessToken();
        String endpoint = properties.endpoint();
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException("clickup.webhook.endpoint must be configured");
        }

        org.example.entity.Webhook oldLocal = webhookRepository.findFirstByWorkspaceId(workspaceId).orElse(null);
        if (oldLocal != null
                && endpoint.equals(oldLocal.getEndpointUrl())
                && oldLocal.getSecret() != null
                && !oldLocal.getSecret().isBlank()
                && "active".equalsIgnoreCase(oldLocal.getStatus())
                && !oldLocal.getUser().getId().equals(token.getUser().getId())) {
            return;
        }

        org.example.clickup.api.WebhooksResponse remoteResponse =
                clickupClient.getWebhooks(workspaceId, accessToken);
        if (remoteResponse == null || remoteResponse.webhooks() == null) {
            throw new IllegalStateException("ClickUp returned no webhook list for workspace " + workspaceId);
        }

        List<String> expectedEvents = ClickUpWebhookCreateRequest.defaultSubscription(endpoint).events();
        Webhook matchingWebhook = remoteResponse.webhooks().stream()
                .filter(remote -> endpoint.equals(remote.endpoint()))
                .findFirst()
                .orElse(null);

        if (matchingWebhook != null
                && sameEvents(matchingWebhook.events(), expectedEvents)
                && !isSuspended(matchingWebhook)) {
            removeSupersededWebhooks(
                    remoteResponse.webhooks(), matchingWebhook.id(), endpoint, oldLocal, accessToken);
            persist(matchingWebhook, token);
            return;
        }

        if (matchingWebhook != null && isSuspended(matchingWebhook)) {
            log.warn(
                    "ClickUp webhook {} is suspended for workspace {}; replacing it.",
                    matchingWebhook.id(),
                    workspaceId);
        }

        ClickUpWebhookCreateRequest createRequest = ClickUpWebhookCreateRequest.defaultSubscription(endpoint);
        WebhookResponse created = clickupClient.createWebhook(workspaceId, accessToken, createRequest);
        if (created == null || created.webhook() == null) {
            throw new IllegalStateException("ClickUp did not return the created webhook for workspace " + workspaceId);
        }
        Webhook newWebhook = created.webhook();
        if (newWebhook.id() == null || newWebhook.secret() == null || newWebhook.secret().isBlank()) {
            throw new IllegalStateException("ClickUp webhook response is missing its ID or signing secret");
        }

        removeSupersededWebhooks(remoteResponse.webhooks(), newWebhook.id(), endpoint, oldLocal, accessToken);
        persist(newWebhook, token);
        log.info("ClickUp webhook synchronized for workspace {}", workspaceId);
    }

    private void removeSupersededWebhooks(
            List<Webhook> remoteWebhooks,
            String retainedWebhookId,
            String endpoint,
            org.example.entity.Webhook oldLocal,
            String accessToken) {
        for (Webhook remote : remoteWebhooks) {
            if (remote.id() != null
                    && !remote.id().equals(retainedWebhookId)
                    && (endpoint.equals(remote.endpoint())
                            || (oldLocal != null && remote.id().equals(oldLocal.getId())))) {
                clickupClient.deleteWebhook(remote.id(), accessToken);
            }
        }
        if (oldLocal != null && !oldLocal.getId().equals(retainedWebhookId)) {
            webhookRepository.delete(oldLocal);
        }
    }

    private boolean sameEvents(List<WebhookEventType> actual, List<String> expected) {
        if (actual == null) {
            return false;
        }
        return actual.stream()
                .map(WebhookEventType::value)
                .sorted()
                .toList()
                .equals(expected.stream().sorted().toList());
    }

    private boolean isSuspended(Webhook webhook) {
        return webhook.health() != null && "suspended".equalsIgnoreCase(webhook.health().status());
    }

    private void persist(Webhook remote, UserTokens token) {
        String workspaceId = token.getWorkspace().getId();
        org.example.entity.Webhook current = webhookRepository.findFirstByWorkspaceId(workspaceId)
                .filter(local -> local.getId().equals(remote.id()))
                .orElseGet(org.example.entity.Webhook::new);
        current.setId(remote.id());
        current.setWorkspace(token.getWorkspace());
        current.setUser(token.getUser());
        current.setEndpointUrl(remote.endpoint());
        current.setSecret(remote.secret());
        current.setStatus(remote.health() == null ? "active" : remote.health().status());
        current.setFailCount(remote.health() == null ? 0 : remote.health().failCount());
        Timestamp now = new Timestamp(System.currentTimeMillis());
        if (current.getCreatedAt() == null) {
            current.setCreatedAt(now);
        }
        current.setUpdatedAt(now);
        webhookRepository.save(current);
    }
}
