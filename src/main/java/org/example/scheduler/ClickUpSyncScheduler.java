package org.example.scheduler;

import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.UserTokens;
import org.example.repository.UserTokensRepository;
import org.example.services.ClickUpFullSyncService;
import org.example.services.ClickUpWebhookSyncService;
import org.example.services.WorkspaceService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ClickUpSyncScheduler {
    private final UserTokensRepository userTokensRepository;
    private final WorkspaceService workspaceService;
    private final ClickUpFullSyncService clickUpFullSyncService;
    private final ClickUpWebhookSyncService clickUpWebhookSyncService;

    @Scheduled(fixedDelayString = "${app.sync.interval-ms:600000}")
    public void runPeriodicSync() {
        log.info("Scheduled ClickUp sync started");
        Set<String> refreshedTokens = new HashSet<>();
        Set<String> syncedWorkspaces = new HashSet<>();

        for (UserTokens token : userTokensRepository.findAll()) {
            if (token.getWorkspace() == null || token.getAccessToken() == null || token.getAccessToken().isBlank()) {
                log.warn("Skipping sync for token record {} without workspace or access token", token.getId());
                continue;
            }

            String workspaceId = token.getWorkspace().getId();
            try {
                if (refreshedTokens.add(token.getAccessToken())) {
                    workspaceService.syncWorkspaces(token.getAccessToken());
                }
                if (syncedWorkspaces.add(workspaceId)) {
                    try {
                        clickUpWebhookSyncService.syncWorkspaceWebhook(token);
                    } catch (RuntimeException e) {
                        log.error("Webhook sync failed for workspace {}", workspaceId, e);
                    }
                    clickUpFullSyncService.syncWorkspaceData(
                            String.valueOf(token.getUser().getId()), workspaceId, token.getAccessToken());
                }
            } catch (RuntimeException e) {
                log.error("Scheduled ClickUp sync failed for workspace {}", workspaceId, e);
            }
        }

        log.info("Scheduled ClickUp sync finished");
    }
}
