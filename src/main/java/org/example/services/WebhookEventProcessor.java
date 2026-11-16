package org.example.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.clickup.webhook.HistoryItem;
import org.example.clickup.webhook.WebhookEvent;
import org.example.clickup.webhook.WebhookEventType;
import org.example.dto.WebhookIngestionPayload;
import org.example.entity.HierarchyEvent;
import org.example.entity.Task;
import org.example.entity.TaskEvent;
import org.example.client.ClickupClient;
import org.example.dto.ListResponseDto;
import org.example.dto.TaskResponseDto;
import org.example.entity.Lists;
import org.example.entity.Task;
import org.example.entity.TaskEvent;
import org.example.entity.User;
import org.example.entity.UserTokens;
import org.example.mapper.ListMapper;
import org.example.mapper.TaskMapper;
import org.example.repository.FolderRepository;
import org.example.repository.HierarchyEventRepository;
import org.example.repository.ListRepository;
import org.example.repository.SpaceRepository;
import org.example.repository.TaskAssigneeRepository;
import org.example.repository.TaskEventRepository;
import org.example.repository.TaskRepository;
import org.example.repository.UserRepository;
import org.example.repository.UserTokensRepository;
import org.example.repository.WebhookRepository;
import org.example.repository.WorkspaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookEventProcessor {
    private final ObjectMapper objectMapper;
    private final TaskRepository taskRepository;
    private final TaskEventRepository taskEventRepository;
    private final HierarchyEventRepository hierarchyEventRepository;
    private final WebhookEventLogService eventLogService;
    private final SseNotificationService sseNotificationService;
    private final ClickupClient clickupClient;
    private final TaskMapper taskMapper;
    private final ListRepository listRepository;
    private final UserRepository userRepository;
    private final UserTokensRepository userTokensRepository;
    private final WebhookRepository webhookRepository;
    private final TaskAssigneeRepository taskAssigneeRepository;
    private final WorkspaceRepository workspaceRepository;
    private final SpaceRepository spaceRepository;
    private final FolderRepository folderRepository;
    private final ListMapper listMapper;

    @Transactional
    public void process(WebhookIngestionPayload payload) {
        WebhookEvent event;
        try {
            event = objectMapper.readValue(payload.getRawJsonPayload(), WebhookEvent.class);
        } catch (JacksonException e) {
            throw new IllegalArgumentException("Invalid ClickUp webhook JSON", e);
        }
        if (event.event() == null || event.event() == WebhookEventType.UNKNOWN) {
            throw new IllegalArgumentException("Webhook event type is missing or unsupported");
        }

        if (event.event().value().startsWith("task")) {
            saveTaskEvents(payload, event);
        } else {
            saveHierarchyEvents(payload, event);
        }
        eventLogService.record(payload, "PROCESSED", null);
    }

    private void saveTaskEvents(WebhookIngestionPayload payload, WebhookEvent event) {
        if (event.taskId() == null || event.taskId().isBlank()) {
            throw new IllegalArgumentException("Task webhook event has no task_id");
        }
        Task task = taskRepository.findById(event.taskId()).orElse(null);
        if (task == null) {
            task = fetchAndSaveNewTask(payload, event);
        }
        if (task == null) {
            throw new IllegalStateException("Webhook task could not be resolved or fetched: " + event.taskId());
        }

        List<HistoryItem> historyItems = event.historyItems() == null ? List.of() : event.historyItems();
        if (historyItems.isEmpty()) {
            saveTaskEvent(payload, event, task, null);
            return;
        }
        for (HistoryItem historyItem : historyItems) {
            saveTaskEvent(payload, event, task, historyItem);
        }
    }

    private Task fetchAndSaveNewTask(WebhookIngestionPayload payload, WebhookEvent event) {
        log.info("Yeni görev tespit edildi (taskCreated). ClickUp API'den çekiliyor: taskId={}", event.taskId());
        String accessToken = resolveAccessToken(payload);
        if (accessToken == null) {
            log.warn("Görev {} için erişim token'ı bulunamadı, API'den çekilemedi.", event.taskId());
            return null;
        }

        try {
            TaskResponseDto.TaskDto taskDto = clickupClient.getTask(event.taskId(), accessToken);
            if (taskDto == null) {
                return null;
            }

            Task task = new Task();
            task.setId(taskDto.id());
            taskMapper.updateEntityFromDto(taskDto, task);

            // 1. Liste çözümleme: Önce taskDto.list().id(), sonra event.listId()
            String listId = (taskDto.list() != null && taskDto.list().id() != null)
                    ? taskDto.list().id()
                    : event.listId();
            if (listId != null && !listId.isBlank()) {
                var listOpt = listRepository.findById(listId);
                if (listOpt.isEmpty()) {
                    // Liste henüz DB'de yoksa ClickUp'tan çekip kaydet
                    syncListFromClickup(payload, listId);
                    listOpt = listRepository.findById(listId);
                }
                listOpt.ifPresent(l -> {
                    task.setList(l);
                    if (l.getSpace() != null && l.getSpace().getWorkspace() != null) {
                        task.setWorkspace(l.getSpace().getWorkspace());
                    }
                });
            }

            // 2. Workspace çözümleme: Eğer workspace hala atanmadıysa
            if (task.getWorkspace() == null) {
                String wsId = null;
                if (taskDto.teamId() != null && !taskDto.teamId().isBlank()) {
                    wsId = taskDto.teamId();
                } else if (payload.getWebhookId() != null) {
                    var webhookOpt = webhookRepository.findById(payload.getWebhookId());
                    if (webhookOpt.isPresent() && webhookOpt.get().getWorkspace() != null) {
                        wsId = webhookOpt.get().getWorkspace().getId();
                    }
                }
                if (wsId != null) {
                    workspaceRepository.findById(wsId).ifPresent(task::setWorkspace);
                }
            }

            if (taskDto.creator() != null && taskDto.creator().id() != null) {
                try {
                    Long creatorId = Long.valueOf(taskDto.creator().id().toString());
                    userRepository.findById(creatorId).ifPresent(task::setCreator);
                } catch (NumberFormatException ignored) {
                }
            }

            // Parent görevi bağlama (Bu bir subtask ise)
            if (taskDto.parent() != null && !taskDto.parent().isBlank()) {
                var parentOpt = taskRepository.findById(taskDto.parent());
                if (parentOpt.isPresent()) {
                    task.setParent(parentOpt.get());
                } else {
                    try {
                        TaskResponseDto.TaskDto parentDto = clickupClient.getTask(taskDto.parent(), accessToken);
                        if (parentDto != null) {
                            Task parentTask = new Task();
                            parentTask.setId(parentDto.id());
                            taskMapper.updateEntityFromDto(parentDto, parentTask);
                            parentTask.setList(task.getList());
                            parentTask.setWorkspace(task.getWorkspace());
                            taskRepository.save(parentTask);
                            task.setParent(parentTask);
                            log.info("Subtask {} için parent task {} veritabanına kaydedildi ve bağlandı.", task.getId(), parentTask.getId());
                        }
                    } catch (Exception e) {
                        log.warn("Subtask {} için parent task {} çekilemedi: {}", task.getId(), taskDto.parent(), e.getMessage());
                    }
                }
            }

            Task saved = taskRepository.save(task);
            log.info("Yeni görev veritabanına kaydedildi: taskId={}, name={}, listId={}, workspaceId={}, parentId={}",
                    saved.getId(), saved.getName(),
                    saved.getList() != null ? saved.getList().getId() : "null",
                    saved.getWorkspace() != null ? saved.getWorkspace().getId() : "null",
                    saved.getParent() != null ? saved.getParent().getId() : "null");

            // Subtask'ları varsa kaydet (Bu bir parent task ise)
            if (taskDto.subtasks() != null && !taskDto.subtasks().isEmpty()) {
                for (TaskResponseDto.TaskDto subDto : taskDto.subtasks()) {
                    if (subDto.id() != null) {
                        Task subTask = taskRepository.findById(subDto.id()).orElseGet(Task::new);
                        subTask.setId(subDto.id());
                        taskMapper.updateEntityFromDto(subDto, subTask);
                        subTask.setParent(saved);
                        if (saved.getList() != null) subTask.setList(saved.getList());
                        if (saved.getWorkspace() != null) subTask.setWorkspace(saved.getWorkspace());
                        taskRepository.save(subTask);
                    }
                }
                log.info("Parent task {} için {} adet subtask kaydedildi/bağlandı.", saved.getId(), taskDto.subtasks().size());
            }

            // Yeni görevin assignee'lerini kaydet
            if (taskDto.assignees() != null) {
                for (org.example.dto.UserDto userDto : taskDto.assignees()) {
                    if (userDto.id() != null) {
                        User user = userRepository.findById(userDto.id()).orElseGet(User::new);
                        user.setId(userDto.id());
                        if (userDto.username() != null) user.setUsername(userDto.username());
                        if (userDto.email() != null) user.setEmail(userDto.email());
                        if (userDto.profilePicture() != null) user.setProfilePicture(userDto.profilePicture());
                        userRepository.save(user);

                        org.example.entity.TaskAssignee ta = org.example.entity.TaskAssignee.builder()
                                .task(saved)
                                .user(user)
                                .assignedAt(new java.sql.Timestamp(System.currentTimeMillis()))
                                .build();
                        taskAssigneeRepository.save(ta);
                    }
                }
                log.info("Yeni görev {} için {} assignee kaydedildi.", saved.getId(), taskDto.assignees().size());
            }

            return saved;
        } catch (Exception e) {
            log.error("ClickUp API'den yeni task çekilemedi: taskId={}", event.taskId(), e);
            return null;
        }
    }

    private String resolveAccessToken(WebhookIngestionPayload payload) {
        if (payload.getWebhookId() != null) {
            var webhookOpt = webhookRepository.findById(payload.getWebhookId());
            if (webhookOpt.isPresent() && webhookOpt.get().getUser() != null && webhookOpt.get().getWorkspace() != null) {
                var tokenOpt = userTokensRepository.findByUserIdAndWorkspaceId(
                        webhookOpt.get().getUser().getId(),
                        webhookOpt.get().getWorkspace().getId());
                if (tokenOpt.isPresent() && tokenOpt.get().getAccessToken() != null) {
                    return tokenOpt.get().getAccessToken();
                }
            }
        }
        return userTokensRepository.findAll().stream()
                .map(UserTokens::getAccessToken)
                .filter(t -> t != null && !t.isBlank())
                .findFirst()
                .orElse(null);
    }

    private void saveTaskEvent(WebhookIngestionPayload payload, WebhookEvent event, Task task, HistoryItem history) {
        String idempotencyKey = idempotencyKey(payload, history);
        if (taskEventRepository.existsByIdempotencyKey(idempotencyKey)) {
            return;
        }

        String field = history == null ? null : history.field();
        String fromStatus = null;
        String toStatus = null;
        String fromPriority = null;
        String toPriority = null;

        if ("resolved_items".equalsIgnoreCase(field)) {
            syncResolvedSubtaskStatuses(payload, event, history);
        }

        // 1. Durum (Status) Güncellemesi
        if ("status".equalsIgnoreCase(field) || event.event() == WebhookEventType.TASK_STATUS_UPDATED) {
            fromStatus = history == null ? null : fieldValue(history.before(), "status", "status");
            toStatus = history == null ? null : fieldValue(history.after(), "status", "status");
            if (toStatus != null) {
                task.setStatusName(toStatus);
                JsonNode afterStatus = history.after();
                if (afterStatus != null && afterStatus.isObject()) {
                    JsonNode statusType = afterStatus.get("type");
                    if (statusType != null && statusType.isTextual()) {
                        task.setStatusType(statusType.textValue());
                    }
                }
                taskRepository.save(task);
                log.info("Task {} durumu '{}' olarak güncellendi.", task.getId(), toStatus);
            }
        }

        // 2. Öncelik (Priority) Güncellemesi
        if ("priority".equalsIgnoreCase(field) || event.event() == WebhookEventType.TASK_PRIORITY_UPDATED) {
            fromPriority = history == null ? null : fieldValue(history.before(), "priority", "priority");
            toPriority = history == null ? null : fieldValue(history.after(), "priority", "priority");
            task.setPriority(toPriority);
            taskRepository.save(task);
            log.info("Task {} önceliği '{}' olarak güncellendi.", task.getId(), toPriority);
        }

        // 3. İsim (Task Name) Güncellemesi
        if ("name".equalsIgnoreCase(field)) {
            String newName = history == null ? null : fieldValue(history.after(), "name", "name");
            if (newName != null) {
                task.setName(newName);
                taskRepository.save(task);
                log.info("Task {} ismi '{}' olarak güncellendi.", task.getId(), newName);
            }
        }

        // 4. Atanan Kişi (Assignee) Güncellemesi
        if ("assignee".equalsIgnoreCase(field) || event.event() == WebhookEventType.TASK_ASSIGNEE_UPDATED) {
            // Mevcut tüm assignee'leri sil
            List<org.example.entity.TaskAssignee> existing = taskAssigneeRepository.findByTaskId(task.getId());
            if (!existing.isEmpty()) {
                taskAssigneeRepository.deleteAll(existing);
                taskAssigneeRepository.flush();
            }

            // ClickUp API'den güncel assignee listesini çek
            String accessToken = resolveAccessToken(payload);
            if (accessToken != null) {
                try {
                    TaskResponseDto.TaskDto freshTask = clickupClient.getTask(task.getId(), accessToken);
                    if (freshTask != null && freshTask.assignees() != null) {
                        for (org.example.dto.UserDto userDto : freshTask.assignees()) {
                            if (userDto.id() != null) {
                                User user = userRepository.findById(userDto.id()).orElseGet(User::new);
                                user.setId(userDto.id());
                                if (userDto.username() != null) user.setUsername(userDto.username());
                                if (userDto.email() != null) user.setEmail(userDto.email());
                                if (userDto.profilePicture() != null) user.setProfilePicture(userDto.profilePicture());
                                userRepository.save(user);

                                org.example.entity.TaskAssignee ta = org.example.entity.TaskAssignee.builder()
                                        .task(task)
                                        .user(user)
                                        .assignedAt(new java.sql.Timestamp(System.currentTimeMillis()))
                                        .build();
                                taskAssigneeRepository.save(ta);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Assignee güncelleme için ClickUp API çağrısı başarısız: taskId={}", task.getId(), e);
                }
            }
            log.info("Task {} assignee'leri webhook üzerinden güncellendi.", task.getId());
        }

        // 5. Etiket (Tag) Güncellemesi
        if ("tag".equalsIgnoreCase(field) || "tags".equalsIgnoreCase(field) || event.event() == WebhookEventType.TASK_TAG_UPDATED) {
            String accessToken = resolveAccessToken(payload);
            if (accessToken != null) {
                try {
                    TaskResponseDto.TaskDto freshTask = clickupClient.getTask(task.getId(), accessToken);
                    if (freshTask != null) {
                        List<String> newTags = (freshTask.tags() != null)
                                ? freshTask.tags().stream().map(TaskResponseDto.TagDto::name).filter(Objects::nonNull).toList()
                                : List.of();
                        task.getTags().clear();
                        task.getTags().addAll(newTags);
                        taskRepository.save(task);
                        log.info("Task {} etiketleri webhook üzerinden güncellendi: {}", task.getId(), newTags);
                    }
                } catch (Exception e) {
                    log.warn("Tag güncelleme için ClickUp API çağrısı başarısız: taskId={}", task.getId(), e);
                }
            }
        }

        TaskEvent taskEvent = new TaskEvent();
        taskEvent.setTask(task);
        taskEvent.setEventType(event.event().value());
        taskEvent.setIdempotencyKey(idempotencyKey);
        taskEvent.setTriggerSource("WEBHOOK");
        if (history != null) {
            taskEvent.setHistoryItemId(history.id());
            taskEvent.setTimestamp(history.date() == null ? null : history.date().toString());
            taskEvent.setChangedByUserId(history.user() == null ? null : history.user().id());
            taskEvent.setFieldName(history.field() == null ? null : history.field());
            taskEvent.setOldValue(fieldValue(history.before(), history.field(), history.field()));
            taskEvent.setNewValue(fieldValue(history.after(), history.field(), history.field()));
        }
        taskEventRepository.save(taskEvent);

        // 5. SSE Canlı Bildirimini Frontend'e Gönder
        broadcastToSse(task, event, fromStatus, toStatus, fromPriority, toPriority, payload);
    }

    private void syncResolvedSubtaskStatuses(
            WebhookIngestionPayload payload, WebhookEvent event, HistoryItem history) {
        JsonNode subtaskIds = history.data() == null ? null : history.data().get("subtask_ids");
        if (subtaskIds == null || !subtaskIds.isArray() || subtaskIds.isEmpty()) {
            return;
        }

        String accessToken = resolveAccessToken(payload);
        if (accessToken == null) {
            log.warn(
                    "resolved_items status check skipped: no ClickUp access token for task {}",
                    event.taskId());
            return;
        }

        TaskResponseDto.TaskDto parent = clickupClient.getTask(event.taskId(), accessToken);
        if (parent == null || parent.subtasks() == null) {
            log.warn(
                    "resolved_items status check could not load subtasks for task {}",
                    event.taskId());
            return;
        }

        for (JsonNode subtaskIdNode : subtaskIds) {
            if (!subtaskIdNode.isTextual() || subtaskIdNode.textValue().isBlank()) {
                log.warn("Ignoring invalid subtask ID in resolved_items for task {}", event.taskId());
                continue;
            }

            String subtaskId = subtaskIdNode.textValue();
            TaskResponseDto.TaskDto subtaskDto = parent.subtasks().stream()
                    .filter(candidate -> subtaskId.equals(candidate.id()))
                    .findFirst()
                    .orElse(null);
            if (subtaskDto == null || subtaskDto.status() == null || subtaskDto.status().status() == null) {
                log.warn(
                        "resolved_items status check did not return a status for subtask {}",
                        subtaskId);
                continue;
            }

            Task subtask = taskRepository.findById(subtaskId).orElse(null);
            if (subtask == null) {
                log.warn("Cannot synchronize status for unknown subtask {}", subtaskId);
                continue;
            }

            String oldStatus = subtask.getStatusName();
            String newStatus = subtaskDto.status().status();
            String oldStatusType = subtask.getStatusType();
            String newStatusType = subtaskDto.status().type();
            if (Objects.equals(oldStatus, newStatus) && Objects.equals(oldStatusType, newStatusType)) {
                continue;
            }

            subtask.setStatusName(newStatus);
            subtask.setStatusType(newStatusType);
            taskRepository.save(subtask);

            String childIdempotencyKey = idempotencyKey(payload, history) + ":" + subtaskId;
            if (!taskEventRepository.existsByIdempotencyKey(childIdempotencyKey)) {
                TaskEvent statusEvent = new TaskEvent();
                statusEvent.setTask(subtask);
                statusEvent.setEventType(WebhookEventType.TASK_STATUS_UPDATED.value());
                statusEvent.setFieldName("status");
                statusEvent.setOldValue(oldStatus);
                statusEvent.setNewValue(newStatus);
                statusEvent.setTimestamp(history.date() == null
                        ? Long.toString(System.currentTimeMillis())
                        : history.date().toString());
                statusEvent.setChangedByUserId(history.user() == null ? null : history.user().id());
                statusEvent.setHistoryItemId(history.id());
                statusEvent.setIdempotencyKey(childIdempotencyKey);
                statusEvent.setTriggerSource("WEBHOOK");
                taskEventRepository.save(statusEvent);
            }
            broadcastToSse(subtask, event, oldStatus, newStatus, null, null, payload);
        }
    }

    private void broadcastToSse(
            Task task,
            WebhookEvent event,
            String fromStatus,
            String toStatus,
            String fromPriority,
            String toPriority,
            WebhookIngestionPayload payload) {
        String workspaceId = resolveWorkspaceId(task, payload);
        if (workspaceId == null || workspaceId.isBlank()) {
            log.warn("Task {} için workspaceId çözülemedi, SSE bildirimi atlanıyor.", task.getId());
            return;
        }

        String listId = (task.getList() != null) ? task.getList().getId() : null;
        String eventType = event.event() != null ? event.event().value() : "taskUpdated";
        long timestamp = System.currentTimeMillis();

        Map<String, Object> ssePayload = new HashMap<>();
        ssePayload.put("eventId", task.getId() + ":" + eventType + ":" + timestamp);
        ssePayload.put("taskId", task.getId());
        ssePayload.put("taskName", task.getName() != null ? task.getName() : "Task (" + task.getId() + ")");
        ssePayload.put("workspaceId", workspaceId);
        ssePayload.put("listId", listId);
        ssePayload.put("eventType", eventType);
        ssePayload.put("fromStatus", fromStatus);
        ssePayload.put("toStatus", toStatus);
        ssePayload.put("fromPriority", fromPriority);
        ssePayload.put("toPriority", toPriority);
        ssePayload.put("timestamp", String.valueOf(timestamp));

        // Assignee bilgisi
        var assignees = taskAssigneeRepository.findByTaskId(task.getId());
        if (!assignees.isEmpty() && assignees.get(0).getUser() != null) {
            User u = assignees.get(0).getUser();
            ssePayload.put("assigneeId", String.valueOf(u.getId()));
            ssePayload.put("assigneeName", u.getUsername());
            ssePayload.put("assigneeProfilePicture", u.getProfilePicture());
            ssePayload.put("assignee_profile_picture", u.getProfilePicture());
        }

        log.info("SSE ile workspaceId={} için task-event yayını yapılıyor (Task: {}, event: {})", workspaceId, task.getId(), eventType);
        sseNotificationService.broadcastTaskEvent(workspaceId, ssePayload);
    }

    private String resolveWorkspaceId(Task task, WebhookIngestionPayload payload) {
        if (task.getWorkspace() != null && task.getWorkspace().getId() != null) {
            return task.getWorkspace().getId();
        }
        if (task.getList() != null && task.getList().getSpace() != null && task.getList().getSpace().getWorkspace() != null) {
            return task.getList().getSpace().getWorkspace().getId();
        }
        if (payload != null && payload.getWebhookId() != null) {
            var webhookOpt = webhookRepository.findById(payload.getWebhookId());
            if (webhookOpt.isPresent() && webhookOpt.get().getWorkspace() != null) {
                return webhookOpt.get().getWorkspace().getId();
            }
        }
        return null;
    }

    private void saveHierarchyEvents(WebhookIngestionPayload payload, WebhookEvent event) {
        String eventName = event.event().value();
        String entityType;
        String entityId;
        if (eventName.startsWith("list")) {
            entityType = "LIST";
            entityId = event.listId();
        } else if (eventName.startsWith("folder")) {
            entityType = "FOLDER";
            entityId = event.folderId();
        } else if (eventName.startsWith("space")) {
            entityType = "SPACE";
            entityId = event.spaceId();
        } else if (eventName.startsWith("goal")) {
            entityType = "GOAL";
            entityId = event.goalId();
        } else if (eventName.startsWith("keyResult")) {
            entityType = "KEY_RESULT";
            entityId = event.keyResultId();
        } else {
            throw new IllegalArgumentException("Unsupported hierarchy webhook event: " + eventName);
        }
        if (entityId == null || entityId.isBlank()) {
            throw new IllegalArgumentException("Hierarchy webhook event has no entity ID");
        }

        // 1. Veritabanındaki ilgili hiyerarşi varlığını senkronize et / sil
        if (event.event() == WebhookEventType.LIST_CREATED || event.event() == WebhookEventType.LIST_UPDATED) {
            syncListFromClickup(payload, entityId);
        } else if (event.event() == WebhookEventType.LIST_DELETED) {
            try {
                listRepository.deleteById(entityId);
                log.info("List {} webhook ile silindi.", entityId);
            } catch (Exception e) {
                log.warn("List {} silinemedi: {}", entityId, e.getMessage());
            }
        } else if (event.event() == WebhookEventType.FOLDER_DELETED) {
            try {
                folderRepository.deleteById(entityId);
                log.info("Folder {} webhook ile silindi.", entityId);
            } catch (Exception e) {
                log.warn("Folder {} silinemedi: {}", entityId, e.getMessage());
            }
        } else if (event.event() == WebhookEventType.SPACE_DELETED) {
            try {
                spaceRepository.deleteById(entityId);
                log.info("Space {} webhook ile silindi.", entityId);
            } catch (Exception e) {
                log.warn("Space {} silinemedi: {}", entityId, e.getMessage());
            }
        }

        List<HistoryItem> historyItems = event.historyItems() == null ? List.of() : event.historyItems();
        if (historyItems.isEmpty()) {
            saveHierarchyEvent(payload, event, entityType, entityId, null);
            return;
        }
        for (HistoryItem historyItem : historyItems) {
            saveHierarchyEvent(payload, event, entityType, entityId, historyItem);
        }
    }

    private void syncListFromClickup(WebhookIngestionPayload payload, String listId) {
        String accessToken = resolveAccessToken(payload);
        if (accessToken == null) return;
        try {
            ListResponseDto.ListDto listDto = clickupClient.getList(listId, accessToken);
            if (listDto != null) {
                Lists list = listRepository.findById(listId).orElseGet(Lists::new);
                listMapper.updateEntityFromDto(listDto, list);
                if (listDto.space() != null && listDto.space().id() != null) {
                    spaceRepository.findById(listDto.space().id()).ifPresent(list::setSpace);
                }
                if (listDto.folder() != null && listDto.folder().id() != null) {
                    folderRepository.findById(listDto.folder().id()).ifPresent(list::setFolder);
                }
                listRepository.save(list);
                log.info("List {} webhook ile DB'ye senkronize edildi: name={}", listId, list.getName());
            }
        } catch (Exception e) {
            log.warn("List {} ClickUp API'den çekilemedi: {}", listId, e.getMessage());
        }
    }

    private void saveHierarchyEvent(
            WebhookIngestionPayload payload,
            WebhookEvent event,
            String entityType,
            String entityId,
            HistoryItem history) {
        String idempotencyKey = idempotencyKey(payload, history);
        if (hierarchyEventRepository.existsByIdempotencyKey(idempotencyKey)) {
            return;
        }

        HierarchyEvent hierarchyEvent = HierarchyEvent.builder()
                .entityType(entityType)
                .entityId(entityId)
                .eventType(event.event().value())
                .fieldName(history == null ? null : history.field())
                .oldValue(history == null
                        ? null
                        : fieldValue(history.before(), history.field(), history.field()))
                .newValue(history == null
                        ? null
                        : fieldValue(history.after(), history.field(), history.field()))
                .changedByUserId(history == null || history.user() == null ? null : history.user().id())
                .idempotencyKey(idempotencyKey)
                .triggerSource("WEBHOOK")
                .build();
        hierarchyEventRepository.save(hierarchyEvent);

        // SSE ile hierarchy event'ini frontend'e yayınla
        broadcastHierarchyToSse(payload, event, entityType, entityId);
    }

    private void broadcastHierarchyToSse(
            WebhookIngestionPayload payload,
            WebhookEvent event,
            String entityType,
            String entityId) {
        // workspaceId'yi webhook kaydından çöz
        String workspaceId = null;
        if (payload.getWebhookId() != null) {
            var webhookOpt = webhookRepository.findById(payload.getWebhookId());
            if (webhookOpt.isPresent() && webhookOpt.get().getWorkspace() != null) {
                workspaceId = webhookOpt.get().getWorkspace().getId();
            }
        }
        if (workspaceId == null || workspaceId.isBlank()) {
            log.warn("Hierarchy event {} için workspaceId çözülemedi, SSE bildirimi atlanıyor.", entityId);
            return;
        }

        String eventType = event.event() != null ? event.event().value() : "hierarchyUpdated";
        long timestamp = System.currentTimeMillis();

        Map<String, Object> ssePayload = new HashMap<>();
        ssePayload.put("eventId", entityType + ":" + entityId + ":" + eventType + ":" + timestamp);
        ssePayload.put("entityType", entityType);
        ssePayload.put("entityId", entityId);
        ssePayload.put("eventType", eventType);
        ssePayload.put("workspaceId", workspaceId);
        ssePayload.put("timestamp", String.valueOf(timestamp));

        // İlgili üst ID'leri de ekle (frontend'in hangi sprint/list etkilendiğini bilmesi için)
        if (event.listId() != null) ssePayload.put("listId", event.listId());
        if (event.folderId() != null) ssePayload.put("folderId", event.folderId());
        if (event.spaceId() != null) ssePayload.put("spaceId", event.spaceId());

        log.info("SSE ile workspaceId={} için hierarchy-event yayını yapılıyor (Entity: {} {})", workspaceId, entityType, entityId);
        sseNotificationService.broadcastHierarchyEvent(workspaceId, ssePayload);
    }

    private String fieldValue(JsonNode node, String objectField, String directField) {
        if (node == null || node.isNull()) {
            return null;
        }
        JsonNode value = objectField == null ? null : node.get(objectField);
        if (value == null && directField != null) {
            value = node.get(directField);
        }
        if (value == null) {
            value = node;
        }
        if (value.isObject()) {
            JsonNode name = value.get("status");
            if (name == null) {
                name = value.get("priority");
            }
            if (name != null) {
                value = name;
            }
        }
        return jsonValue(value);
    }

    private Long userId(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        JsonNode id = node.get("id");
        if (id == null || id.isNull()) {
            return null;
        }
        if (id.isNumber()) {
            return id.numberValue().longValue();
        }
        String value = id.textValue();
        if (value == null) {
            throw new IllegalArgumentException("Webhook assignee ID is not a number");
        }
        return Long.valueOf(value);
    }

    private String jsonValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        return node.isTextual() ? node.textValue() : node.toString();
    }

    private String idempotencyKey(WebhookIngestionPayload payload, HistoryItem history) {
        String identity = history == null || history.id() == null
                ? payload.getRawJsonPayload()
                : history.id();
        try {
            byte[] key = (payload.getWebhookId() + ":" + identity).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
