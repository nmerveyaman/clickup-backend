package org.example.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.SprintAnalyticsResponse;
import org.example.dto.StatusDto;
import org.example.dto.TaskAnalyticsResponse;
import org.example.dto.UserDto;
import org.example.dto.UserGanttAnalyticsResponse;
import org.example.entity.Lists;
import org.example.entity.Task;
import org.example.entity.TaskAssignee;
import org.example.entity.TaskEvent;
import org.example.entity.User;
import org.example.repository.TaskAssigneeRepository;
import org.example.repository.TaskEventRepository;
import org.example.repository.TaskRepository;
import org.example.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final TaskEventRepository taskEventRepository;
    private final TaskAssigneeRepository taskAssigneeRepository;

    /** Uses the configured list start date, falling back to the earliest task creation date. */
    public long resolveEffectiveSprintStartDate(Lists listEntity) {
        if (listEntity == null) {
            return -1L;
        }

        if (listEntity.getStartDate() != null && !listEntity.getStartDate().isBlank()) {
            long parsed = parseTimestamp(listEntity.getStartDate());
            if (parsed > 0) {
                return parsed;
            }
        }

        if (listEntity.getId() == null) {
            return -1L;
        }

        return taskRepository.findEffectiveSprintStartDate(listEntity.getId()).orElse(-1L);
    }

    private String resolveStatusType(String statusName, Map<String, String> knownStatusTypes) {
        if (statusName == null)
            return "custom";
        String lower = statusName.toLowerCase(Locale.ROOT).trim();

        if (knownStatusTypes != null && knownStatusTypes.containsKey(lower)) {
            return knownStatusTypes.get(lower);
        }

        if (lower.equals("to do") || lower.equals("todo") || lower.equals("open") || lower.equals("yapılacaklar")) {
            return "open";
        }
        if (lower.equals("complete") || lower.equals("completed") || lower.equals("closed") || lower.equals("done")
                || lower.equals("tamamlandı")) {
            return "closed";
        }
        if (lower.equals("backlog") || lower.equals("unstarted")) {
            return "unstarted";
        }

        return "custom";
    }

    private boolean sameStatus(String first, String second) {
        if (first == null || second == null) {
            return Objects.equals(first, second);
        }
        return first.trim().equalsIgnoreCase(second.trim());
    }

    private String extractFromStatus(TaskEvent event) {
        if (event == null)
            return null;
        if ("status".equalsIgnoreCase(event.getFieldName())
                || (event.getEventType() != null && event.getEventType().toLowerCase().contains("status"))) {
            return event.getOldValue();
        }
        return null;
    }

    private String extractToStatus(TaskEvent event) {
        if (event == null)
            return null;
        if ("status".equalsIgnoreCase(event.getFieldName())
                || (event.getEventType() != null && event.getEventType().toLowerCase().contains("status"))) {
            return event.getNewValue();
        }
        return null;
    }

    private Long extractToAssignee(TaskEvent event) {
        if (event == null)
            return null;
        if ("assignee".equalsIgnoreCase(event.getFieldName())
                || (event.getEventType() != null && event.getEventType().toLowerCase().contains("assignee"))) {
            if (event.getNewValue() != null) {
                try {
                    return Long.parseLong(event.getNewValue().trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return null;
    }

    private int countStatusRollbacks(List<TaskEvent> events, Map<String, Integer> statusOrderIndices) {
        int rollbackCount = 0;

        List<TaskEvent> statusEvents = events.stream()
                .filter(Objects::nonNull)
                .filter(event -> extractFromStatus(event) != null && extractToStatus(event) != null)
                .filter(event -> event.getTimestamp() != null && parseTimestamp(event.getTimestamp()) > 0L)
                .sorted(Comparator.comparingLong(event -> parseTimestamp(event.getTimestamp())))
                .toList();

        for (TaskEvent event : statusEvents) {
            String fromStatus = extractFromStatus(event).trim();
            String toStatus = extractToStatus(event).trim();
            if (sameStatus(fromStatus, toStatus)) {
                continue;
            }

            Integer fromOrder = statusOrderIndices.get(fromStatus.toLowerCase(Locale.ROOT));
            Integer toOrder = statusOrderIndices.get(toStatus.toLowerCase(Locale.ROOT));
            if (fromOrder != null && toOrder != null && toOrder < fromOrder) {
                rollbackCount++;
            }
        }

        return rollbackCount;
    }

    private static class StatusSegment {
        String statusName;
        long durationMillis = 0;
        Map<Long, Long> assigneeMillis = new HashMap<>();

        StatusSegment(String statusName) {
            this.statusName = statusName;
        }

        void addDuration(long duration, Long assigneeId) {
            this.durationMillis += duration;
            if (assigneeId != null) {
                this.assigneeMillis.merge(assigneeId, duration, Long::sum);
            }
        }
    }

    private static class ColumnDetailAccumulator {
        String statusName;
        String statusType;
        long durationMillis;
        Map<Long, Long> assigneeMillis = new HashMap<>();

        ColumnDetailAccumulator(String statusName, String statusType) {
            this.statusName = statusName;
            this.statusType = statusType;
        }

        void add(StatusSegment segment) {
            durationMillis += segment.durationMillis;
            segment.assigneeMillis.forEach((userId, duration) -> assigneeMillis.merge(userId, duration, Long::sum));
        }
    }

    public TaskAnalyticsResponse getComprehensiveAnalytics(
            Task task,
            List<TaskEvent> events,
            Lists listEntity) {
        Map<String, String> knownStatusTypes = new HashMap<>();
        Map<String, Integer> statusOrderIndices = new HashMap<>();
        if (listEntity != null && listEntity.getStatuses() != null) {
            for (StatusDto status : listEntity.getStatuses()) {
                if (status.status() != null) {
                    String normalizedStatus = status.status().toLowerCase(Locale.ROOT).trim();
                    if (status.type() != null) {
                        knownStatusTypes.put(normalizedStatus, status.type().toLowerCase(Locale.ROOT));
                    }
                    if (status.orderIndex() != null) {
                        statusOrderIndices.put(normalizedStatus, status.orderIndex());
                    }
                }
            }
        }

        if (task.getStatusName() != null && task.getStatusType() != null) {
            knownStatusTypes.put(
                    task.getStatusName().toLowerCase(Locale.ROOT).trim(),
                    task.getStatusType().toLowerCase(Locale.ROOT));
        }

        Long effectiveSprintStart = listEntity == null
                ? null
                : resolveEffectiveSprintStartDate(listEntity);

        List<Long> assignees = taskAssigneeRepository.findByTaskId(task.getId()).stream()
                .filter(a -> a.getUser() != null)
                .map(a -> a.getUser().getId())
                .toList();

        TaskAnalyticsResponse response =
                getComprehensiveAnalytics(task, events, knownStatusTypes, statusOrderIndices,
                        effectiveSprintStart, assignees,
                        task.getTags() != null ? new ArrayList<>(task.getTags()) : Collections.emptyList(), null);
        List<Task> childTasks = taskRepository.findByParentId(task.getId());
        response.setSubtaskCount(childTasks.size());
        response.setSubtasks(childTasks.stream()
                .map(child -> new TaskAnalyticsResponse.SubtaskItemDto(
                        child.getId(), child.getName(), child.getStatusName(), child.getStatusType()))
                .toList());
        if (task.getParent() != null
                && (response.getParentName() == null || response.getParentName().isBlank())) {
            response.setParentId(task.getParent().getId());
            String parentName = task.getParent().getName();
            response.setParentName(parentName != null && !parentName.isBlank()
                    ? parentName
                    : taskRepository.findById(task.getParent().getId())
                            .map(Task::getName)
                            .orElse(null));
        }
        return response;
    }

    public TaskAnalyticsResponse getAssignedTaskAnalytics(
            Task task,
            List<TaskEvent> events,
            List<Long> taskAssignees) {
        Map<String, String> knownStatusTypes = new HashMap<>();
        Map<String, Integer> statusOrderIndices = new HashMap<>();
        Lists listEntity = task.getList();
        if (listEntity != null && listEntity.getStatuses() != null) {
            for (StatusDto status : listEntity.getStatuses()) {
                if (status.status() == null) {
                    continue;
                }
                String normalizedStatus = status.status().toLowerCase(Locale.ROOT).trim();
                if (status.type() != null) {
                    knownStatusTypes.put(normalizedStatus, status.type().toLowerCase(Locale.ROOT));
                }
                if (status.orderIndex() != null) {
                    statusOrderIndices.put(normalizedStatus, status.orderIndex());
                }
            }
        }
        if (task.getStatusName() != null && task.getStatusType() != null) {
            knownStatusTypes.put(
                    task.getStatusName().toLowerCase(Locale.ROOT).trim(),
                    task.getStatusType().toLowerCase(Locale.ROOT));
        }

        List<String> taskTags = task.getTags() == null
                ? Collections.emptyList()
                : new ArrayList<>(task.getTags());
        return getComprehensiveAnalytics(
                task,
                events,
                knownStatusTypes,
                statusOrderIndices,
                null,
                taskAssignees,
                taskTags,
                Collections.emptyMap());
    }

    public EstimateComparison compareTimeEstimate(String rawEstimate, long actualActiveMillis, boolean completed) {
        if (rawEstimate == null || rawEstimate.isBlank()) {
            return null;
        }

        final long estimateMillis;
        try {
            estimateMillis = Long.parseLong(rawEstimate.trim());
        } catch (NumberFormatException e) {
            return new EstimateComparison(null, null, null, "UNAVAILABLE");
        }
        if (estimateMillis <= 0L) {
            return null;
        }
        if (actualActiveMillis < 0L) {
            return new EstimateComparison(formatDuration(estimateMillis), null, null, "UNAVAILABLE");
        }

        long differenceMillis = Math.abs(actualActiveMillis - estimateMillis);
        boolean overEstimate = actualActiveMillis > estimateMillis;
        double usagePercentage = Math.round((double) actualActiveMillis / estimateMillis * 1000.0) / 10.0;
        String outcome = overEstimate
                ? (completed ? "OVER_ESTIMATE_COMPLETED" : "OVER_ESTIMATE")
                : (completed ? "MET_ESTIMATE" : "WITHIN_ESTIMATE");
        return new EstimateComparison(
                formatDuration(estimateMillis),
                usagePercentage,
                formatDuration(differenceMillis),
                outcome);
    }

    public record EstimateComparison(
            String estimatedTime,
            Double usagePercentage,
            String variance,
            String outcome) {}

    public TaskAnalyticsResponse getComprehensiveAnalytics(
            Task task,
            List<TaskEvent> events,
            Map<String, String> knownStatusTypes,
            Long effectiveSprintStart,
            List<Long> taskAssignees) {
        Map<String, Integer> statusOrderIndices = new HashMap<>();
        Lists listEntity = task.getList();
        if (listEntity != null && listEntity.getStatuses() != null) {
            for (StatusDto status : listEntity.getStatuses()) {
                if (status.status() != null && status.orderIndex() != null) {
                    statusOrderIndices.put(
                            status.status().toLowerCase(Locale.ROOT).trim(),
                            status.orderIndex());
                }
            }
        }
        return getComprehensiveAnalytics(task, events, knownStatusTypes, statusOrderIndices,
                effectiveSprintStart, taskAssignees,
                task.getTags() != null ? new ArrayList<>(task.getTags()) : Collections.emptyList(), null);
    }

    private TaskAnalyticsResponse getComprehensiveAnalytics(
            Task task,
            List<TaskEvent> events,
            Map<String, String> knownStatusTypes,
            Map<String, Integer> statusOrderIndices,
            Long effectiveSprintStart,
            List<Long> taskAssignees,
            List<String> taskTags,
            Map<Long, User> assigneeUsers) {
        TaskAnalyticsResponse response = new TaskAnalyticsResponse();
        response.setTaskId(task.getId());
        response.setTaskName(task.getName());
        response.setCurrentStatus(task.getStatusName());
        response.setPriority(task.getPriority());
        response.setStartDate(task.getDateCreated());

        List<TaskEvent> safeEvents = (events == null) ? Collections.emptyList() : events;

        String currentTaskType = resolveStatusType(task.getStatusName(), knownStatusTypes);
        boolean isCurrentlyCompleted = "closed".equalsIgnoreCase(currentTaskType)
                || "done".equalsIgnoreCase(currentTaskType);
        String realEndDate = isCurrentlyCompleted
                ? (task.getDateDone() != null ? task.getDateDone() : task.getDateClosed())
                : null;
        response.setEndDate(realEndDate);

        response.setRollbackCount(countStatusRollbacks(safeEvents, statusOrderIndices));

        String blockedById = task.getBlockedByTask() != null ? task.getBlockedByTask().getId() : null;
        response.setBlockedByTaskId(blockedById);
        response.setTimeEstimate(task.getTimeEstimate());
        response.setTags(taskTags);
        response.setAssigneeIds(taskAssignees != null ? taskAssignees : Collections.emptyList());

        if (taskAssignees != null && !taskAssignees.isEmpty()) {
            Long primaryId = taskAssignees.get(0);
            response.setAssigneeId(primaryId);
            User assignee = assigneeUsers == null
                    ? userRepository.findById(primaryId).orElse(null)
                    : assigneeUsers.get(primaryId);
            if (assignee != null) {
                response.setAssigneeName(assignee.getUsername());
                response.setAssigneeProfilePicture(assignee.getProfilePicture());
            }
        }
        response.setSubtaskCount(0);
        response.setSubtasks(Collections.emptyList());

        long taskCreatedMillisForScope = parseTimestamp(task.getDateCreated());
        long scopeCreepGracePeriodMillis = 24L * 60L * 60L * 1000L;
        boolean scopeCreepDetected = taskCreatedMillisForScope > 0L
                && effectiveSprintStart != null
                && effectiveSprintStart > 0L
                && taskCreatedMillisForScope - effectiveSprintStart >= scopeCreepGracePeriodMillis;
        response.setScopeCreep(scopeCreepDetected);

        long taskCreatedMillis = parseTimestamp(task.getDateCreated());
        if (taskCreatedMillis <= 0L) {
            return response;
        }

        long parsedEndTimestamp = realEndDate != null ? parseTimestamp(realEndDate) : -1L;
        long taskEndTimestamp = parsedEndTimestamp > 0L
                ? Math.max(taskCreatedMillis, parsedEndTimestamp)
                : System.currentTimeMillis();
        response.setTotalTaskDuration(formatDuration(taskEndTimestamp - taskCreatedMillis));

        List<TaskEvent> sortedEvents = safeEvents.stream()
                .filter(Objects::nonNull)
                .filter(event -> event.getTimestamp() != null
                        && parseTimestamp(event.getTimestamp()) > 0L
                        && parseTimestamp(event.getTimestamp()) <= taskEndTimestamp)
                .sorted(Comparator.comparing(event -> parseTimestamp(event.getTimestamp())))
                .toList();

        List<StatusSegment> segments = new ArrayList<>();
        long lastTimestamp = taskCreatedMillis;
        long currentStatusStartedAt = taskCreatedMillis;
        Long currentAssignee = (taskAssignees != null && !taskAssignees.isEmpty()) ? taskAssignees.get(0) : null;

        String currentStatus = task.getStatusName();
        if (!sortedEvents.isEmpty()) {
            String initialFrom = extractFromStatus(sortedEvents.get(0));
            if (initialFrom != null) {
                currentStatus = initialFrom;
            }
        }

        StatusSegment activeSegment = currentStatus != null ? new StatusSegment(currentStatus) : null;

        for (TaskEvent event : sortedEvents) {
            long eventTimestamp = parseTimestamp(event.getTimestamp());
            if (eventTimestamp <= 0L || eventTimestamp < lastTimestamp) {
                String toStatus = extractToStatus(event);
                if (toStatus != null) {
                    currentStatus = toStatus;
                }
                Long toAssignee = extractToAssignee(event);
                if (toAssignee != null) {
                    currentAssignee = toAssignee;
                }
                continue;
            }

            long duration = Math.max(0L, eventTimestamp - lastTimestamp);

            if (activeSegment != null) {
                activeSegment.addDuration(duration, currentAssignee);
            }

            String toStatus = extractToStatus(event);
            if (toStatus != null && !sameStatus(toStatus, currentStatus)) {
                if (activeSegment != null) {
                    segments.add(activeSegment);
                }
                currentStatus = toStatus;
                currentStatusStartedAt = eventTimestamp;
                activeSegment = new StatusSegment(currentStatus);
            }

            Long toAssignee = extractToAssignee(event);
            if (toAssignee != null) {
                currentAssignee = toAssignee;
            }

            lastTimestamp = eventTimestamp;
        }

        long endTimestamp = taskEndTimestamp;
        if (activeSegment != null && endTimestamp > lastTimestamp) {
            long finalDuration = Math.max(0L, endTimestamp - lastTimestamp);
            activeSegment.addDuration(finalDuration, currentAssignee);
            segments.add(activeSegment);
        } else if (activeSegment != null && segments.isEmpty()) {
            segments.add(activeSegment);
        }

        long twoDaysMillis = 2L * 24L * 60L * 60L * 1000L;
        boolean isStalled = !isCurrentlyCompleted
                && activeSegment != null
                && sameStatus(currentStatus, task.getStatusName())
                && "custom".equalsIgnoreCase(resolveStatusType(task.getStatusName(), knownStatusTypes))
                && endTimestamp - currentStatusStartedAt > twoDaysMillis;
        response.setStalled(isStalled);

        long totalActiveMillis = 0;
        long totalWaitingMillis = 0;
        Map<String, ColumnDetailAccumulator> columnDetailAccumulators = new LinkedHashMap<>();
        List<TaskAnalyticsResponse.ColumnVisit> columnHistory = new ArrayList<>();
        Map<String, Integer> columnVisitCounts = new HashMap<>();
        Map<Long, Long> rawUserActiveMillis = new HashMap<>();
        Map<Long, Long> rawUserWaitingMillis = new HashMap<>();

        for (StatusSegment seg : segments) {
            String rawStatusName = seg.statusName;
            if (rawStatusName == null) {
                continue;
            }
            String lowerName = rawStatusName.toLowerCase(Locale.ROOT).trim();
            String type = resolveStatusType(rawStatusName, knownStatusTypes);

            if ("custom".equalsIgnoreCase(type)) {
                totalActiveMillis += seg.durationMillis;
                for (Map.Entry<Long, Long> assigneeEntry : seg.assigneeMillis.entrySet()) {
                    rawUserActiveMillis.merge(assigneeEntry.getKey(), assigneeEntry.getValue(), Long::sum);
                }
            } else if ("unstarted".equalsIgnoreCase(type)) {
                totalWaitingMillis += seg.durationMillis;
                for (Map.Entry<Long, Long> assigneeEntry : seg.assigneeMillis.entrySet()) {
                    rawUserWaitingMillis.merge(assigneeEntry.getKey(), assigneeEntry.getValue(), Long::sum);
                }
            }

            Map<Long, String> visitAssigneeTimes = new HashMap<>();
            seg.assigneeMillis.forEach((userId, duration) -> visitAssigneeTimes.put(userId, formatDuration(duration)));
            int visitNumber = columnVisitCounts.merge(lowerName, 1, Integer::sum);
            columnHistory.add(new TaskAnalyticsResponse.ColumnVisit(
                    rawStatusName,
                    visitNumber,
                    type,
                    formatDuration(seg.durationMillis),
                    visitAssigneeTimes));

            columnDetailAccumulators.computeIfAbsent(
                    lowerName,
                    key -> new ColumnDetailAccumulator(rawStatusName, type)).add(seg);
        }

        Map<String, TaskAnalyticsResponse.ColumnDetail> columnDetailsMap = new LinkedHashMap<>();
        for (ColumnDetailAccumulator accumulator : columnDetailAccumulators.values()) {
            Map<Long, String> formattedAssigneeTimes = new HashMap<>();
            accumulator.assigneeMillis
                    .forEach((userId, duration) -> formattedAssigneeTimes.put(userId, formatDuration(duration)));

            TaskAnalyticsResponse.ColumnDetail detail = new TaskAnalyticsResponse.ColumnDetail();
            detail.setStatusType(accumulator.statusType);
            detail.setTotalDurationFormatted(formatDuration(accumulator.durationMillis));
            detail.setAssigneeTimes(formattedAssigneeTimes);
            columnDetailsMap.put(accumulator.statusName, detail);
        }

        Map<Long, String> formattedUserActiveTimes = new HashMap<>();
        for (Map.Entry<Long, Long> entry : rawUserActiveMillis.entrySet()) {
            formattedUserActiveTimes.put(entry.getKey(), formatDuration(entry.getValue()));
        }
        response.setUserActiveWorkingTimes(formattedUserActiveTimes);

        Map<Long, String> formattedUserWaitingTimes = new HashMap<>();
        for (Map.Entry<Long, Long> entry : rawUserWaitingMillis.entrySet()) {
            formattedUserWaitingTimes.put(entry.getKey(), formatDuration(entry.getValue()));
        }
        response.setUserWaitingTimes(formattedUserWaitingTimes);

        response.setTotalActiveWorkingTime(formatDuration(totalActiveMillis));
        response.setTotalActiveWorkingTimeMillis(totalActiveMillis);
        response.setTotalWaitingTime(formatDuration(totalWaitingMillis));
        response.setColumnDetails(columnDetailsMap);
        response.setColumnHistory(columnHistory);

        return response;
    }

    public SprintAnalyticsResponse getSprintComprehensiveAnalytics(Lists listEntity, List<Task> tasks) {
        SprintAnalyticsResponse sprintResponse = new SprintAnalyticsResponse();
        if (listEntity != null) {
            sprintResponse.setListId(listEntity.getId());
            sprintResponse.setListName(listEntity.getName());
            sprintResponse.setSprintDueDate(listEntity.getDueDate());
            sprintResponse.setAvailableColumns(
                    listEntity.getStatuses() != null ? listEntity.getStatuses() : Collections.emptyList());
        }

        if (tasks == null || tasks.isEmpty()) {
            sprintResponse.setTotalTasks(0);
            sprintResponse.setCompletionRatio("0 / 0");
            sprintResponse.setTotalSprintTaskDuration("0 Saniye");
            sprintResponse.setTotalSprintActiveWorkingTime("0 Saniye");
            sprintResponse.setTotalSprintWaitingTime("0 Saniye");
            sprintResponse.setScopeCreepTasks(Collections.emptyList());
            sprintResponse.setStalledTasks(Collections.emptyList());
            sprintResponse.setBlockedTasks(Collections.emptyList());
            sprintResponse.setUserTotalActiveWorkingTimes(Collections.emptyMap());
            sprintResponse.setUserTotalWaitingTimes(Collections.emptyMap());
            sprintResponse.setTaskAnalyticsList(Collections.emptyList());
            sprintResponse.setUsersMap(Collections.emptyMap());
            sprintResponse.setUserTaskCounts(Collections.emptyMap());
            sprintResponse.setTaskCountPerColumn(Collections.emptyMap());
            return sprintResponse;
        }

        // 1. Sprint Başlangıç Tarihi
        long effectiveStart = resolveEffectiveSprintStartDate(listEntity);
        sprintResponse.setEffectiveSprintStartDate(effectiveStart != -1L ? effectiveStart : null);

        // 2. N+1 Önlemek İçin Task ID'leri topla
        List<String> taskIds = tasks.stream().map(Task::getId).filter(Objects::nonNull).toList();

        // Event'leri tek sorguda çek
        List<TaskEvent> allEvents = taskEventRepository.findByTaskIdIn(taskIds);
        Map<String, List<TaskEvent>> eventsByTaskId = (allEvents != null) ? allEvents.stream()
                .filter(e -> e.getTask() != null)
                .collect(Collectors.groupingBy(e -> e.getTask().getId())) : Collections.emptyMap();

        // Assignee'leri tek sorguda çek
        List<TaskAssignee> allAssignees = taskAssigneeRepository.findByTaskIdIn(taskIds);
        Map<String, List<Long>> assigneesByTaskId = new HashMap<>();
        Set<Long> uniqueSprintAssignees = new HashSet<>();
        Map<String, Integer> userTaskCounts = new HashMap<>();

        if (allAssignees != null) {
            for (TaskAssignee ta : allAssignees) {
                if (ta.getTask() != null && ta.getUser() != null) {
                    String tId = ta.getTask().getId();
                    Long uId = ta.getUser().getId();
                    assigneesByTaskId.computeIfAbsent(tId, k -> new ArrayList<>()).add(uId);
                    uniqueSprintAssignees.add(uId);
                    String uIdStr = String.valueOf(uId);
                    userTaskCounts.put(uIdStr, userTaskCounts.getOrDefault(uIdStr, 0) + 1);
                }
            }
        }
        Map<Long, User> assigneeUsersById = new HashMap<>();
        if (!uniqueSprintAssignees.isEmpty()) {
            userRepository.findAllById(uniqueSprintAssignees).forEach(user -> {
                if (user.getId() != null) {
                    assigneeUsersById.put(user.getId(), user);
                }
            });
        }

        // 3. Statü Tiplerini Haritala
        Map<String, String> knownStatusTypes = new HashMap<>();
        Map<String, Integer> statusOrderIndices = new HashMap<>();
        if (listEntity != null && listEntity.getStatuses() != null) {
            for (StatusDto status : listEntity.getStatuses()) {
                if (status.status() != null) {
                    String normalizedStatus = status.status().toLowerCase(Locale.ROOT).trim();
                    if (status.type() != null) {
                        knownStatusTypes.put(normalizedStatus, status.type().toLowerCase(Locale.ROOT));
                    }
                    if (status.orderIndex() != null) {
                        statusOrderIndices.put(normalizedStatus, status.orderIndex());
                    }
                }
            }
        }
        for (Task t : tasks) {
            if (t.getStatusName() != null && t.getStatusType() != null) {
                knownStatusTypes.put(t.getStatusName().toLowerCase().trim(), t.getStatusType().toLowerCase());
            }
        }

        int completedCount = 0;
        int totalRollbacks = 0;

        List<SprintAnalyticsResponse.TaskSummaryDto> scopeCreepList = new ArrayList<>();
        List<SprintAnalyticsResponse.TaskSummaryDto> stalledList = new ArrayList<>();
        List<SprintAnalyticsResponse.TaskSummaryDto> blockedList = new ArrayList<>();

        long sprintTotalTaskDurationMillis = 0;
        long sprintTotalActiveMillis = 0;
        long sprintTotalWaitingMillis = 0;
        Map<String, Long> customColumnTotalsMillis = new LinkedHashMap<>();
        Map<Long, List<long[]>> userActiveIntervalsMap = new HashMap<>();
        Map<Long, List<long[]>> userWaitingIntervalsMap = new HashMap<>();
        List<TaskAnalyticsResponse> taskResponses = new ArrayList<>();
        Map<String, Integer> taskCountPerColumn = new HashMap<>();

        Map<String, List<String>> tagsByTaskId = new HashMap<>();
        if (!taskIds.isEmpty()) {
            for (Object[] row : taskRepository.findTagsByTaskIdIn(taskIds)) {
                if (row.length >= 2 && row[0] != null && row[1] != null) {
                    tagsByTaskId.computeIfAbsent((String) row[0], ignored -> new ArrayList<>())
                            .add((String) row[1]);
                }
            }
        }

        Map<String, String> parentNamesMap = new HashMap<>();
        List<String> parentIds = tasks.stream()
                .map(Task::getParent)
                .filter(Objects::nonNull)
                .map(Task::getId)
                .distinct()
                .toList();
        if (!parentIds.isEmpty()) {
            taskRepository.findAllById(parentIds).forEach(pt -> {
                if (pt != null && pt.getName() != null) {
                    parentNamesMap.put(pt.getId(), pt.getName());
                }
            });
        }

        Map<String, List<TaskAnalyticsResponse.SubtaskItemDto>> subtasksByParentId = new HashMap<>();
        Map<String, Integer> subtaskCountsByTaskId = new HashMap<>();
        if (!taskIds.isEmpty()) {
            try {
                List<Task> allChildTasks = taskRepository.findByParentIdIn(taskIds);
                if (allChildTasks != null) {
                    for (Task ct : allChildTasks) {
                        if (ct.getParent() != null && ct.getParent().getId() != null) {
                            String parentId = ct.getParent().getId();
                            subtasksByParentId.computeIfAbsent(parentId, k -> new ArrayList<>())
                                    .add(new TaskAnalyticsResponse.SubtaskItemDto(
                                            ct.getId(),
                                            ct.getName(),
                                            ct.getStatusName(),
                                            ct.getStatusType()));
                            subtaskCountsByTaskId.merge(parentId, 1, Integer::sum);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Alt görevler çekilirken hata oluştu: {}", e.getMessage());
            }
        }

        for (Task task : tasks) {
            if (task.getStatusName() != null) {
                String cleanStatus = task.getStatusName().toLowerCase(Locale.ROOT).trim();
                taskCountPerColumn.put(cleanStatus, taskCountPerColumn.getOrDefault(cleanStatus, 0) + 1);
            }

            List<Long> taskAssignees = assigneesByTaskId.getOrDefault(task.getId(), Collections.emptyList());
            List<TaskEvent> taskEvents = eventsByTaskId.getOrDefault(task.getId(), Collections.emptyList());

            TaskAnalyticsResponse taskAnalytics = getComprehensiveAnalytics(
                    task, taskEvents, knownStatusTypes, statusOrderIndices, effectiveStart, taskAssignees,
                    tagsByTaskId.getOrDefault(task.getId(), Collections.emptyList()), assigneeUsersById);
            taskAnalytics.setSubtaskCount(subtaskCountsByTaskId.getOrDefault(task.getId(), 0));
            taskAnalytics.setSubtasks(subtasksByParentId.getOrDefault(task.getId(), Collections.emptyList()));
            if (task.getParent() != null) {
                taskAnalytics.setParentId(task.getParent().getId());
                String pName = (task.getParent().getName() != null && !task.getParent().getName().isBlank())
                        ? task.getParent().getName()
                        : parentNamesMap.get(task.getParent().getId());
                taskAnalytics.setParentName(pName);
            }
            taskResponses.add(taskAnalytics);

            String currentType = resolveStatusType(task.getStatusName(), knownStatusTypes);
            if ("closed".equalsIgnoreCase(currentType) || "done".equalsIgnoreCase(currentType)
                    || taskAnalytics.getEndDate() != null) {
                completedCount++;
            }

            SprintAnalyticsResponse.TaskSummaryDto summary = new SprintAnalyticsResponse.TaskSummaryDto(
                    task.getId(), task.getName(), task.getStatusName());

            if (taskAnalytics.isScopeCreep()) {
                scopeCreepList.add(summary);
            }
            if (taskAnalytics.isStalled()) {
                stalledList.add(summary);
            }
            if (task.getBlockedByTask() != null) {
                blockedList.add(summary);
            }

            totalRollbacks += taskAnalytics.getRollbackCount();

            long taskCreatedMillis = parseTimestamp(task.getDateCreated());
            if (taskCreatedMillis > 0L) {
                long parsedTaskEnd = taskAnalytics.getEndDate() != null
                        ? parseTimestamp(taskAnalytics.getEndDate())
                        : -1L;
                long taskEndTimestamp = parsedTaskEnd > 0L
                        ? Math.max(taskCreatedMillis, parsedTaskEnd)
                        : System.currentTimeMillis();
                sprintTotalTaskDurationMillis += taskEndTimestamp - taskCreatedMillis;

                List<TaskEvent> sortedEvents = taskEvents.stream()
                        .filter(Objects::nonNull)
                        .filter(event -> event.getTimestamp() != null
                                && parseTimestamp(event.getTimestamp()) > 0L
                                && parseTimestamp(event.getTimestamp()) <= taskEndTimestamp)
                        .sorted(Comparator.comparing(event -> parseTimestamp(event.getTimestamp())))
                        .toList();

                long lastTimestamp = taskCreatedMillis;
                Long currentAssignee = !taskAssignees.isEmpty() ? taskAssignees.get(0) : null;
                if (currentAssignee != null) {
                    uniqueSprintAssignees.add(currentAssignee);
                }

                String currentStatus = task.getStatusName();
                if (!sortedEvents.isEmpty()) {
                    String initialFrom = extractFromStatus(sortedEvents.get(0));
                    if (initialFrom != null) {
                        currentStatus = initialFrom;
                    }
                }

                for (TaskEvent event : sortedEvents) {
                    long eventTimestamp = parseTimestamp(event.getTimestamp());
                    if (eventTimestamp <= 0L || eventTimestamp < lastTimestamp) {
                        String toStatus = extractToStatus(event);
                        if (toStatus != null)
                            currentStatus = toStatus;
                        Long toAssignee = extractToAssignee(event);
                        if (toAssignee != null) {
                            currentAssignee = toAssignee;
                            uniqueSprintAssignees.add(currentAssignee);
                        }
                        continue;
                    }

                    long duration = Math.max(0L, eventTimestamp - lastTimestamp);
                    if (currentStatus != null && duration > 0L) {
                        String type = resolveStatusType(currentStatus, knownStatusTypes);
                        if ("custom".equalsIgnoreCase(type)) {
                            String cleanStatus = currentStatus.toLowerCase(Locale.ROOT).trim();
                            customColumnTotalsMillis.merge(cleanStatus, duration, Long::sum);
                            sprintTotalActiveMillis += duration;

                            if (currentAssignee != null) {
                                userActiveIntervalsMap.computeIfAbsent(currentAssignee, k -> new ArrayList<>())
                                        .add(new long[] { lastTimestamp, eventTimestamp });
                            }
                        } else if ("unstarted".equalsIgnoreCase(type)) {
                            sprintTotalActiveMillis += duration;
                            sprintTotalWaitingMillis += duration;
                            if (currentAssignee != null) {
                                userWaitingIntervalsMap.computeIfAbsent(currentAssignee, k -> new ArrayList<>())
                                        .add(new long[] { lastTimestamp, eventTimestamp });
                            }
                        }
                    }

                    String toStatus = extractToStatus(event);
                    if (toStatus != null)
                        currentStatus = toStatus;
                    Long toAssignee = extractToAssignee(event);
                    if (toAssignee != null) {
                        currentAssignee = toAssignee;
                        uniqueSprintAssignees.add(currentAssignee);
                    }
                    lastTimestamp = eventTimestamp;
                }

                if (currentStatus != null && taskEndTimestamp > lastTimestamp) {
                    long finalDuration = Math.max(0L, taskEndTimestamp - lastTimestamp);
                    String type = resolveStatusType(currentStatus, knownStatusTypes);

                    if ("custom".equalsIgnoreCase(type) && finalDuration > 0L) {
                        String cleanStatus = currentStatus.toLowerCase(Locale.ROOT).trim();
                        customColumnTotalsMillis.merge(cleanStatus, finalDuration, Long::sum);
                        sprintTotalActiveMillis += finalDuration;

                        if (currentAssignee != null) {
                            userActiveIntervalsMap.computeIfAbsent(currentAssignee, k -> new ArrayList<>())
                                    .add(new long[] { lastTimestamp, taskEndTimestamp });
                        }
                    } else if ("unstarted".equalsIgnoreCase(type) && finalDuration > 0L) {
                        sprintTotalActiveMillis += finalDuration;
                        sprintTotalWaitingMillis += finalDuration;
                        if (currentAssignee != null) {
                            userWaitingIntervalsMap.computeIfAbsent(currentAssignee, k -> new ArrayList<>())
                                    .add(new long[] { lastTimestamp, taskEndTimestamp });
                        }
                    }
                }
            }
        }

        Set<Long> missingUserIds = new HashSet<>(uniqueSprintAssignees);
        missingUserIds.removeAll(assigneeUsersById.keySet());
        if (!missingUserIds.isEmpty()) {
            userRepository.findAllById(missingUserIds).forEach(user -> {
                if (user.getId() != null) {
                    assigneeUsersById.put(user.getId(), user);
                }
            });
        }
        Map<Long, UserDto> usersMap = new HashMap<>();
        for (User user : assigneeUsersById.values()) {
            usersMap.put(user.getId(), new UserDto(
                    user.getId(),
                    user.getUsername(),
                    user.getEmail(),
                    user.getProfilePicture(),
                    user.getRole() != null ? user.getRole() : "DEVELOPER",
                    null,
                    null));
        }
        sprintResponse.setUsersMap(usersMap);
        sprintResponse.setUserTaskCounts(userTaskCounts);
        sprintResponse.setTaskCountPerColumn(taskCountPerColumn);

        int totalTasks = tasks.size();
        sprintResponse.setTotalTasks(totalTasks);
        sprintResponse.setCompletedTasks(completedCount);
        sprintResponse.setCompletionRatio(completedCount + " / " + totalTasks);
        sprintResponse.setTotalActiveAssignees(uniqueSprintAssignees.size());

        sprintResponse.setScopeCreepTaskCount(scopeCreepList.size());
        sprintResponse.setScopeCreepTasks(scopeCreepList);

        sprintResponse.setStalledTaskCount(stalledList.size());
        sprintResponse.setStalledTasks(stalledList);

        sprintResponse.setBlockedTaskCount(blockedList.size());
        sprintResponse.setBlockedTasks(blockedList);

        sprintResponse.setTotalRollbackCount(totalRollbacks);
        sprintResponse.setTotalSprintTaskDuration(formatDuration(sprintTotalTaskDurationMillis));
        sprintResponse.setTotalSprintActiveWorkingTime(formatDuration(sprintTotalActiveMillis));
        sprintResponse.setTotalSprintWaitingTime(formatDuration(sprintTotalWaitingMillis));

        String bottleneck = null;
        long maxCustomMillis = -1L;
        for (Map.Entry<String, Long> entry : customColumnTotalsMillis.entrySet()) {
            if (entry.getValue() > maxCustomMillis && entry.getValue() > 0) {
                maxCustomMillis = entry.getValue();
                bottleneck = entry.getKey();
            }
        }
        sprintResponse.setBottleneckColumn(bottleneck);

        Map<Long, String> formattedUserTotals = new HashMap<>();
        for (Map.Entry<Long, List<long[]>> entry : userActiveIntervalsMap.entrySet()) {
            long netUserMillis = calculateMergedDurationMillis(entry.getValue());
            formattedUserTotals.put(entry.getKey(), formatDuration(netUserMillis));
        }
        sprintResponse.setUserTotalActiveWorkingTimes(formattedUserTotals);

        Map<Long, String> formattedUserWaitingTotals = new HashMap<>();
        for (Map.Entry<Long, List<long[]>> entry : userWaitingIntervalsMap.entrySet()) {
            long netUserMillis = calculateMergedDurationMillis(entry.getValue());
            formattedUserWaitingTotals.put(entry.getKey(), formatDuration(netUserMillis));
        }
        sprintResponse.setUserTotalWaitingTimes(formattedUserWaitingTotals);
        sprintResponse.setTaskAnalyticsList(taskResponses);

        return sprintResponse;
    }

    public UserGanttAnalyticsResponse getUserGanttAnalytics(Long userId, String listId, List<Task> tasks) {
        UserGanttAnalyticsResponse response = new UserGanttAnalyticsResponse();
        response.setUserId(userId);
        response.setListId(listId);

        if (tasks == null || tasks.isEmpty()) {
            response.setNetTotalActiveWorkingTime("0 Saniye");
            response.setRawTotalTaskWorkingTime("0 Saniye");
            response.setTaskTimelines(Collections.emptyList());
            return response;
        }

        List<String> taskIds = tasks.stream().map(Task::getId).filter(Objects::nonNull).toList();
        List<TaskEvent> allEvents = taskEventRepository.findByTaskIdIn(taskIds);
        Map<String, List<TaskEvent>> eventsByTaskId = (allEvents != null) ? allEvents.stream()
                .filter(e -> e.getTask() != null)
                .collect(Collectors.groupingBy(e -> e.getTask().getId())) : Collections.emptyMap();

        List<TaskAssignee> allAssignees = taskAssigneeRepository.findByTaskIdIn(taskIds);
        Map<String, List<Long>> assigneesByTaskId = new HashMap<>();
        if (allAssignees != null) {
            for (TaskAssignee ta : allAssignees) {
                if (ta.getTask() != null && ta.getUser() != null) {
                    assigneesByTaskId.computeIfAbsent(ta.getTask().getId(), k -> new ArrayList<>())
                            .add(ta.getUser().getId());
                }
            }
        }

        Map<String, String> knownStatusTypes = new HashMap<>();
        for (Task t : tasks) {
            if (t.getStatusName() != null && t.getStatusType() != null) {
                knownStatusTypes.put(t.getStatusName().toLowerCase().trim(), t.getStatusType().toLowerCase());
            }
        }

        List<long[]> allUserActiveIntervals = new ArrayList<>();
        long rawTotalMillis = 0L;
        List<UserGanttAnalyticsResponse.UserTaskGanttDto> taskTimelines = new ArrayList<>();

        for (Task task : tasks) {
            long taskCreatedMillis = parseTimestamp(task.getDateCreated());
            if (taskCreatedMillis <= 0L)
                continue;

            List<TaskEvent> sortedEvents = eventsByTaskId.getOrDefault(task.getId(), Collections.emptyList())
                    .stream()
                    .filter(Objects::nonNull)
                    .filter(event -> event.getTimestamp() != null && parseTimestamp(event.getTimestamp()) > 0L)
                    .sorted(Comparator.comparing(event -> parseTimestamp(event.getTimestamp())))
                    .toList();

            String currentTaskType = resolveStatusType(task.getStatusName(), knownStatusTypes);
            boolean isCurrentlyCompleted = "closed".equalsIgnoreCase(currentTaskType)
                    || "done".equalsIgnoreCase(currentTaskType);
            String realEndDate = isCurrentlyCompleted
                    ? (task.getDateDone() != null ? task.getDateDone() : task.getDateClosed())
                    : null;

            long lastTimestamp = taskCreatedMillis;
            List<Long> taskAssignees = assigneesByTaskId.getOrDefault(task.getId(), Collections.emptyList());
            Long currentAssignee = !taskAssignees.isEmpty() ? taskAssignees.get(0) : null;
            String currentStatus = task.getStatusName();
            if (!sortedEvents.isEmpty()) {
                String initialFrom = extractFromStatus(sortedEvents.get(0));
                if (initialFrom != null)
                    currentStatus = initialFrom;
            }

            List<UserGanttAnalyticsResponse.WorkIntervalDto> userIntervalsForThisTask = new ArrayList<>();
            long userTaskTotalMillis = 0L;

            for (TaskEvent event : sortedEvents) {
                long eventTimestamp = parseTimestamp(event.getTimestamp());
                if (eventTimestamp <= 0L || eventTimestamp < lastTimestamp) {
                    String toStatus = extractToStatus(event);
                    if (toStatus != null)
                        currentStatus = toStatus;
                    Long toAssignee = extractToAssignee(event);
                    if (toAssignee != null)
                        currentAssignee = toAssignee;
                    continue;
                }

                if (eventTimestamp > lastTimestamp && currentStatus != null
                        && Objects.equals(currentAssignee, userId)) {
                    String type = resolveStatusType(currentStatus, knownStatusTypes);
                    if ("custom".equalsIgnoreCase(type)) {
                        long duration = eventTimestamp - lastTimestamp;
                        if (duration > 0L) {
                            userIntervalsForThisTask.add(new UserGanttAnalyticsResponse.WorkIntervalDto(
                                    currentStatus, lastTimestamp, eventTimestamp, formatDuration(duration), duration));
                            allUserActiveIntervals.add(new long[] { lastTimestamp, eventTimestamp });
                            userTaskTotalMillis += duration;
                            rawTotalMillis += duration;
                        }
                    }
                }

                String toStatus = extractToStatus(event);
                if (toStatus != null)
                    currentStatus = toStatus;
                Long toAssignee = extractToAssignee(event);
                if (toAssignee != null)
                    currentAssignee = toAssignee;
                lastTimestamp = eventTimestamp;
            }

            long endTimestamp = (realEndDate != null) ? parseTimestamp(realEndDate) : System.currentTimeMillis();
            if (endTimestamp > lastTimestamp && currentStatus != null && Objects.equals(currentAssignee, userId)) {
                String type = resolveStatusType(currentStatus, knownStatusTypes);
                if ("custom".equalsIgnoreCase(type)) {
                    long duration = endTimestamp - lastTimestamp;
                    if (duration > 0L) {
                        userIntervalsForThisTask.add(new UserGanttAnalyticsResponse.WorkIntervalDto(
                                currentStatus, lastTimestamp, endTimestamp, formatDuration(duration), duration));
                        allUserActiveIntervals.add(new long[] { lastTimestamp, endTimestamp });
                        userTaskTotalMillis += duration;
                        rawTotalMillis += duration;
                    }
                }
            }

            if (!userIntervalsForThisTask.isEmpty()) {
                long firstStart = userIntervalsForThisTask.get(0).startTimestamp();
                long lastEnd = userIntervalsForThisTask.get(userIntervalsForThisTask.size() - 1).endTimestamp();

                taskTimelines.add(new UserGanttAnalyticsResponse.UserTaskGanttDto(
                        task.getId(),
                        task.getName(),
                        task.getStatusName(),
                        formatDuration(userTaskTotalMillis),
                        firstStart,
                        lastEnd,
                        userIntervalsForThisTask));
            }
        }

        taskTimelines.sort(Comparator.comparingLong(UserGanttAnalyticsResponse.UserTaskGanttDto::getFirstStartedAt));

        long netMergedMillis = calculateMergedDurationMillis(allUserActiveIntervals);

        response.setNetTotalActiveMillis(netMergedMillis);
        response.setNetTotalActiveWorkingTime(formatDuration(netMergedMillis));
        response.setRawTotalTaskWorkingTime(formatDuration(rawTotalMillis));
        response.setTotalAssignedTasksCount(taskTimelines.size());
        response.setTaskTimelines(taskTimelines);

        return response;
    }

    private String formatDuration(long millis) {
        if (millis <= 0) {
            return "0 Saniye";
        }
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        long remainingHours = hours % 24;
        long remainingMinutes = minutes % 60;
        long remainingSeconds = seconds % 60;

        if (days > 0) {
            return String.format("%d Gün %d Saat %d Dakika %d Saniye", days, remainingHours, remainingMinutes,
                    remainingSeconds);
        } else if (hours > 0) {
            return String.format("%d Saat %d Dakika %d Saniye", hours, remainingMinutes, remainingSeconds);
        } else if (minutes > 0) {
            return String.format("%d Dakika %d Saniye", minutes, remainingSeconds);
        } else {
            return String.format("%d Saniye", Math.max(1, remainingSeconds));
        }
    }

    private long parseTimestamp(String timestampStr) {
        if (timestampStr == null || timestampStr.isBlank()) {
            return -1L;
        }

        try {
            long parsed = Long.parseLong(timestampStr.trim());
            return parsed > 0 ? parsed : -1L;
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private long calculateMergedDurationMillis(List<long[]> intervals) {
        if (intervals == null || intervals.isEmpty())
            return 0L;

        List<long[]> sorted = new ArrayList<>(intervals);
        sorted.sort(Comparator.comparingLong(a -> a[0]));

        List<long[]> merged = new ArrayList<>();
        long[] current = sorted.get(0).clone();
        merged.add(current);

        for (int i = 1; i < sorted.size(); i++) {
            long[] next = sorted.get(i);
            if (next[0] <= current[1]) {
                current[1] = Math.max(current[1], next[1]);
            } else {
                current = next.clone();
                merged.add(current);
            }
        }

        long netTotalMillis = 0L;
        for (long[] interval : merged) {
            netTotalMillis += Math.max(0L, interval[1] - interval[0]);
        }
        return netTotalMillis;
    }
}
