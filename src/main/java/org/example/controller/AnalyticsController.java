package org.example.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.SprintAnalyticsResponse;
import org.example.dto.TaskAnalyticsResponse;
import org.example.dto.UserGanttAnalyticsResponse;
import org.example.entity.Lists;
import org.example.entity.Task;
import org.example.entity.TaskEvent;
import org.example.repository.ListRepository;
import org.example.repository.TaskEventRepository;
import org.example.repository.TaskRepository;
import org.example.services.AnalyticsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final TaskRepository taskRepository;
    private final TaskEventRepository taskEventRepository;
    private final ListRepository listRepository;

    /**
     * 1. Tekil Görev (Task) Kapsamlı Analiz Endpoint'i
     */
    @GetMapping("/task/{taskId}")
    public ResponseEntity<TaskAnalyticsResponse> getTaskAnalytics(@PathVariable String taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task bulunamadı: " + taskId));

        List<TaskEvent> events = taskEventRepository.findByTaskIdOrderByTimestampAsc(taskId);

        Lists listEntity = task.getList();

        TaskAnalyticsResponse response = analyticsService.getComprehensiveAnalytics(task, events, listEntity);
        return ResponseEntity.ok(response);
    }

    /**
     * 2. Sprint (List) Bazlı Toplu Özet Analiz Endpoint'i
     */
    @GetMapping("/sprint/{listId}")
    public ResponseEntity<SprintAnalyticsResponse> getSprintAnalytics(@PathVariable String listId) {
        String cleanListId = (listId != null) ? listId.trim() : "";
        log.info("Sprint analizi isteniyor. Liste ID: '{}'", cleanListId);

        Lists listEntity = listRepository.findById(cleanListId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Sprint / Liste veritabanında bulunamadı: '" + cleanListId + "'"
                ));

        List<Task> tasks = taskRepository.findByListId(cleanListId);
        log.info("Liste ID '{}' için veritabanında {} adet görev bulundu.", cleanListId, tasks.size());

        SprintAnalyticsResponse response = analyticsService.getSprintComprehensiveAnalytics(listEntity, tasks);
        return ResponseEntity.ok(response);
    }

    /**
     * 3. Kullanıcı (Assignee) Bazlı Gantt Chart ve Net Çalışma Süresi Endpoint'i
     * (Belirli bir sprint için ?listId=... gönderilebilir; gönderilmezse kullanıcının tüm görevlerini tarar)
     */
    @GetMapping("/user/{userId}/gantt")
    public ResponseEntity<UserGanttAnalyticsResponse> getUserGanttAnalytics(
            @PathVariable Long userId,
            @RequestParam(required = false) String listId) {

        List<Task> tasks = (listId != null && !listId.isBlank())
                ? taskRepository.findByListId(listId.trim())
                : taskRepository.findAll();

        UserGanttAnalyticsResponse response = analyticsService.getUserGanttAnalytics(userId, listId, tasks);
        return ResponseEntity.ok(response);
    }
}
