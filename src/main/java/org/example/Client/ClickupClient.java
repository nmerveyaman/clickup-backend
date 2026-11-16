package org.example.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;
import org.example.clickup.api.WebhookResponse;
import org.example.clickup.api.WebhooksResponse;
import org.example.config.ClickUpApiProperties;
import org.example.dto.ClickUpWebhookCreateRequest;
import org.example.dto.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ClickupClient {

    private final RestClient restClient;
    private final ClickUpApiProperties properties;

    public record UserWrapper(@JsonProperty("user") UserDto user) {}

    public ClickupClient(RestClient restClient, ClickUpApiProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    // --- Auth Endpoints ---

    public OAuthDto.Response getAccessToken(String code) {
        return restClient
                .post()
                .uri(uriBuilder -> uriBuilder
                        .path("/oauth/token")
                        .queryParam("client_id", properties.clientId())
                        .queryParam("client_secret", properties.clientSecret())
                        .queryParam("code", code)
                        .build())
                .retrieve()
                .body(OAuthDto.Response.class);
    }

    public UserDto getAuthorizedUser(String accessToken) {
        UserWrapper wrapper = restClient
                .get()
                .uri("/user")
                .header("Authorization", accessToken)
                .retrieve()
                .body(UserWrapper.class);

        return wrapper != null ? wrapper.user() : null;
    }

    // --- Data Sync Endpoints ---

    public WorkspaceResponseDto getWorkspaces(String accessToken) {
        return restClient
                .get()
                .uri(properties.endpoints().teams())
                .header("Authorization", accessToken)
                .retrieve()
                .body(WorkspaceResponseDto.class);
    }

    public SpaceResponseDto getSpaces(String workspaceId, String accessToken) {
        return restClient
                .get()
                .uri(properties.endpoints().spaces(), workspaceId)
                .header("Authorization", accessToken)
                .retrieve()
                .body(SpaceResponseDto.class);
    }

    public FolderResponseDto getFolders(String spaceId, String accessToken) {
        return restClient
                .get()
                .uri(properties.endpoints().folders(), spaceId)
                .header("Authorization", accessToken)
                .retrieve()
                .body(FolderResponseDto.class);
    }

    public ListResponseDto getFolderlessLists(String spaceId, String accessToken) {
        return restClient
                .get()
                .uri(properties.endpoints().spaceList(), spaceId)
                .header("Authorization", accessToken)
                .retrieve()
                .body(ListResponseDto.class);
    }

    public ListResponseDto.ListDto getList(String listId, String accessToken) {
        return restClient
                .get()
                .uri("/list/{listId}", listId)
                .header("Authorization", accessToken)
                .retrieve()
                .body(ListResponseDto.ListDto.class);
    }

    public ListResponseDto getFolderLists(String folderId, String accessToken) {
        return restClient
                .get()
                .uri("/folder/{folderId}/list", folderId)
                .header("Authorization", accessToken)
                .retrieve()
                .body(ListResponseDto.class);
    }

    public TaskResponseDto getTasks(String listId, String accessToken) {
        int page = 0;
        List<TaskResponseDto.TaskDto> tasks = new ArrayList<>();

        while (true) {
            int requestedPage = page;
            TaskResponseDto response = restClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/list/{listId}/task")
                            .queryParam("subtasks", true)
                            .queryParam("include_closed", true)
                            .queryParam("archived", false)
                            .queryParam("page", requestedPage)
                            .build(listId))
                    .header("Authorization", accessToken)
                    .retrieve()
                    .body(TaskResponseDto.class);

            if (response == null || response.tasks() == null) {
                if (page == 0) {
                    return response;
                }
                throw new IllegalStateException(
                        "ClickUp returned an empty task page " + requestedPage + " for list " + listId);
            }
            if (response.tasks().isEmpty()) {
                break;
            }

            tasks.addAll(response.tasks());
            if (response.tasks().size() < 100) {
                break;
            }
            page++;
        }

        return new TaskResponseDto(tasks);
    }

    public TaskResponseDto.TaskDto getTask(String taskId, String accessToken) {
        return restClient
                .get()
                .uri(uriBuilder -> uriBuilder
                        .path("/task/{taskId}")
                        .queryParam("include_subtasks", "true")
                        .build(taskId))
                .header("Authorization", accessToken)
                .retrieve()
                .body(TaskResponseDto.TaskDto.class);
    }

    public WebhooksResponse getWebhooks(String workspaceId, String accessToken) {
        return restClient
                .get()
                .uri("/team/{workspaceId}/webhook", workspaceId)
                .header("Authorization", accessToken)
                .retrieve()
                .body(WebhooksResponse.class);
    }

    public WebhookResponse createWebhook(
            String workspaceId, String accessToken, ClickUpWebhookCreateRequest request) {
        return restClient
                .post()
                .uri("/team/{workspaceId}/webhook", workspaceId)
                .header("Authorization", accessToken)
                .body(request)
                .retrieve()
                .body(WebhookResponse.class);
    }

    public void deleteWebhook(String webhookId, String accessToken) {
        restClient
                .delete()
                .uri("/webhook/{webhookId}", webhookId)
                .header("Authorization", accessToken)
                .retrieve()
                .toBodilessEntity();
    }
}
