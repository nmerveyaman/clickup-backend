package org.example.services;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.client.ClickupClient;
import org.example.dto.TaskResponseDto;
import org.example.dto.UserDto;
import org.example.entity.Lists;
import org.example.entity.Task;
import org.example.entity.TaskAssignee;
import org.example.entity.User;
import org.example.mapper.TaskMapper;
import org.example.repository.ListRepository;
import org.example.repository.TaskAssigneeRepository;
import org.example.repository.TaskRepository;
import org.example.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskAssigneeRepository taskAssigneeRepository;
    private final ListRepository listRepository;
    private final UserRepository userRepository;
    private final ClickupClient client;
    private final TaskMapper taskMapper;
    private final SyncEventRecorder syncEventRecorder;

    @Transactional
    public void syncListTasks(String listId, String userAccessToken) {
        TaskResponseDto response;

        try {
            log.info("List ID: {} için görevler ClickUp API'den isteniyor...", listId);
            response = client.getTasks(listId, userAccessToken);

        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn(
                    "List ID: {} için yetki bulunamadı (401 Unauthorized / OAUTH_192). Hata: {}",
                    listId,
                    e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("List ID: {} görevleri çekilirken hata oluştu.", listId, e);
            throw new IllegalStateException("ClickUp görevleri senkronize edilemedi: " + listId, e);
        }

        if (response == null || response.tasks() == null) {
            throw new IllegalStateException("ClickUp task response was empty for list " + listId);
        }

        Lists list = listRepository
                .findById(listId)
                .orElseThrow(() -> new RuntimeException("Liste veritabanında bulunamadı: " + listId));
        Integer oldTotalTaskCount = list.getTotalTaskCount();
        Integer oldSubtaskCount = list.getSubtaskCount();
        List<TaskResponseDto.TaskDto> tasks = response.tasks();
        Set<String> currentTaskIds = tasks.stream()
                .map(TaskResponseDto.TaskDto::id)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        for (TaskResponseDto.TaskDto taskDto : tasks) {
            try {
                Task task = taskRepository.findById(taskDto.id()).orElseGet(Task::new);
                String oldName = task.getName();
                String oldStatus = task.getStatusName();
                String oldStatusType = task.getStatusType();
                Boolean oldArchived = task.getArchived();
                String oldPriority = task.getPriority();
                String oldTimeEstimate = task.getTimeEstimate();
                String oldDateCreated = task.getDateCreated();
                String oldDateUpdated = task.getDateUpdated();
                String oldDateClosed = task.getDateClosed();
                String oldDateDone = task.getDateDone();
                String oldUrl = task.getUrl();
                Long oldCreatorId = task.getCreator() == null ? null : task.getCreator().getId();
                String oldParentId = task.getParent() == null ? null : task.getParent().getId();
                String oldTopLevelParentId =
                        task.getTopLevelParent() == null ? null : task.getTopLevelParent().getId();
                List<Long> oldAssigneeIds = taskAssigneeRepository.findByTaskId(taskDto.id()).stream()
                        .filter(assignee -> assignee.getUser() != null)
                        .map(assignee -> assignee.getUser().getId())
                        .toList();

                // Alanların DTO'dan Entity'ye aktarımı (MapStruct)
                taskMapper.updateEntityFromDto(taskDto, task);

                // İlişkilerin kurulması
                task.setList(list);
                task.setWorkspace(list.getSpace() != null ? list.getSpace().getWorkspace() : null);

                // Creator (Long ID dönüşümü)
                if (taskDto.creator() != null && taskDto.creator().id() != null) {
                    try {
                        Long creatorId = Long.valueOf(taskDto.creator().id().toString());
                        userRepository.findById(creatorId).ifPresent(task::setCreator);
                    } catch (NumberFormatException e) {
                        log.warn(
                                "Task Creator ID sayıya dönüştürülemedi: {}",
                                taskDto.creator().id());
                    }
                }

                // Parent Task ilişkileri
                if (taskDto.parent() != null && !taskDto.parent().isBlank()) {
                    var parentOpt = taskRepository.findById(taskDto.parent());
                    if (parentOpt.isPresent()) {
                        task.setParent(parentOpt.get());
                    } else {
                        // Parent görev DB'de henüz yoksa (örn: subtask önce işleniyorsa veya farklı sayfadaysa) placeholder oluştur
                        Task placeholderParent = new Task();
                        placeholderParent.setId(taskDto.parent());
                        placeholderParent.setList(list);
                        placeholderParent.setWorkspace(list.getSpace() != null ? list.getSpace().getWorkspace() : null);
                        taskRepository.save(placeholderParent);
                        task.setParent(placeholderParent);
                    }
                }
                if (taskDto.topLevelParent() != null
                        && !taskDto.topLevelParent().isBlank()) {
                    taskRepository.findById(taskDto.topLevelParent()).ifPresent(task::setTopLevelParent);
                }

                Task savedTask = taskRepository.save(task);

                // Eğer bu task'ın subtask'ları DTO'da geldiyse onları da bağla
                if (taskDto.subtasks() != null && !taskDto.subtasks().isEmpty()) {
                    for (TaskResponseDto.TaskDto subDto : taskDto.subtasks()) {
                        if (subDto.id() != null) {
                            Task subTask = taskRepository.findById(subDto.id()).orElseGet(Task::new);
                            subTask.setId(subDto.id());
                            taskMapper.updateEntityFromDto(subDto, subTask);
                            subTask.setParent(savedTask);
                            subTask.setList(list);
                            subTask.setWorkspace(list.getSpace() != null ? list.getSpace().getWorkspace() : null);
                            taskRepository.save(subTask);
                        }
                    }
                }

                syncTaskAssignees(savedTask, taskDto.assignees());

                String newStatus = savedTask.getStatusName();
                String newPriority = savedTask.getPriority();
                if (taskDto.status() == null || taskDto.status().status() == null) {
                    log.warn(
                            "ClickUp response did not include a status for task {}; keeping stored status '{}'.",
                            savedTask.getId(),
                            oldStatus);
                }
                if (!java.util.Objects.equals(oldStatus, newStatus)) {
                    log.info(
                            "Task status changed during ClickUp sync: taskId={}, from='{}', to='{}'.",
                            savedTask.getId(),
                            oldStatus,
                            newStatus);
                    syncEventRecorder.recordTaskChange(savedTask, "status", oldStatus, newStatus);
                }
                if (!java.util.Objects.equals(oldPriority, newPriority)) {
                    syncEventRecorder.recordTaskChange(savedTask, "priority", oldPriority, newPriority);
                }

                List<Long> newAssigneeIds = taskDto.assignees() == null
                        ? oldAssigneeIds
                        : taskDto.assignees().stream()
                                .map(UserDto::id)
                                .filter(java.util.Objects::nonNull)
                                .distinct()
                                .toList();
                for (Long removedId : oldAssigneeIds) {
                    if (!newAssigneeIds.contains(removedId)) {
                        syncEventRecorder.recordTaskChange(savedTask, "assignee", removedId, null);
                    }
                }
                for (Long addedId : newAssigneeIds) {
                    if (!oldAssigneeIds.contains(addedId)) {
                        syncEventRecorder.recordTaskChange(savedTask, "assignee", null, addedId);
                    }
                }

                syncEventRecorder.recordTaskChange(savedTask, "name", oldName, savedTask.getName());
                syncEventRecorder.recordTaskChange(
                        savedTask, "status_type", oldStatusType, savedTask.getStatusType());
                syncEventRecorder.recordTaskChange(savedTask, "archived", oldArchived, savedTask.getArchived());
                syncEventRecorder.recordTaskChange(
                        savedTask, "time_estimate", oldTimeEstimate, savedTask.getTimeEstimate());
                syncEventRecorder.recordTaskChange(
                        savedTask, "date_created", oldDateCreated, savedTask.getDateCreated());
                syncEventRecorder.recordTaskChange(
                        savedTask, "date_updated", oldDateUpdated, savedTask.getDateUpdated());
                syncEventRecorder.recordTaskChange(
                        savedTask, "date_closed", oldDateClosed, savedTask.getDateClosed());
                syncEventRecorder.recordTaskChange(savedTask, "date_done", oldDateDone, savedTask.getDateDone());
                syncEventRecorder.recordTaskChange(
                        savedTask,
                        "parent",
                        oldParentId,
                        savedTask.getParent() == null ? null : savedTask.getParent().getId());
                syncEventRecorder.recordTaskChange(
                        savedTask,
                        "top_level_parent",
                        oldTopLevelParentId,
                        savedTask.getTopLevelParent() == null ? null : savedTask.getTopLevelParent().getId());
                syncEventRecorder.recordTaskChange(savedTask, "url", oldUrl, savedTask.getUrl());
                syncEventRecorder.recordTaskChange(
                        savedTask,
                        "creator",
                        oldCreatorId,
                        savedTask.getCreator() == null ? null : savedTask.getCreator().getId());

            } catch (Exception e) {
                log.error("Task ID: {} kaydedilirken hata oluştu.", taskDto.id(), e);
                throw new IllegalStateException("ClickUp görevi kaydedilemedi: " + taskDto.id(), e);
            }
        }
        int parentTaskCount = (int) tasks.stream()
                .filter(task -> task.parent() == null || task.parent().isBlank())
                .count();
        int totalTaskCount = tasks.size();
        list.setTotalTaskCount(totalTaskCount);
        list.setSubtaskCount(totalTaskCount - parentTaskCount);
        listRepository.save(list);
        syncEventRecorder.recordHierarchyChange(
                "LIST", listId, "total_task_count", oldTotalTaskCount, list.getTotalTaskCount());
        syncEventRecorder.recordHierarchyChange(
                "LIST", listId, "subtask_count", oldSubtaskCount, list.getSubtaskCount());

        log.info(
                "List ID: {} için {} görev senkronize edildi (subtask: {}).",
                listId,
                totalTaskCount,
                list.getSubtaskCount());
        archiveMissingTasks(listId, currentTaskIds);
    }

    private void archiveMissingTasks(String listId, Set<String> currentTaskIds) {
        for (Task task : taskRepository.findByListId(listId)) {
            if (!currentTaskIds.contains(task.getId()) && !Boolean.TRUE.equals(task.getArchived())) {
                Boolean oldArchived = task.getArchived();
                task.setArchived(true);
                taskRepository.save(task);
                syncEventRecorder.recordTaskChange(task, "archived", oldArchived, true);
            }
        }
    }

    private void syncTaskAssignees(Task task, List<UserDto> assigneesDto) {
        if (assigneesDto == null) {
            return;
        }

        List<TaskAssignee> existingAssignees = taskAssigneeRepository.findByTaskId(task.getId());
        if (!existingAssignees.isEmpty()) {
            taskAssigneeRepository.deleteAll(existingAssignees);
        }

        if (assigneesDto.isEmpty()) {
            return;
        }

        for (UserDto userDto : assigneesDto) {
            if (userDto.id() != null) {
                try {
                    Long userId = Long.valueOf(userDto.id().toString());
                    User user = userRepository.findById(userId).orElseGet(User::new);
                    user.setId(userId);
                    if (userDto.username() != null) user.setUsername(userDto.username());
                    if (userDto.email() != null) user.setEmail(userDto.email());
                    if (userDto.profilePicture() != null) user.setProfilePicture(userDto.profilePicture());
                    userRepository.save(user);

                    TaskAssignee taskAssignee =
                            TaskAssignee.builder().task(task).user(user).build();
                    taskAssigneeRepository.save(taskAssignee);
                } catch (NumberFormatException e) {
                    log.warn("Assignee User ID sayıya dönüştürülemedi: {}", userDto.id());
                }
            }
        }
    }
}
