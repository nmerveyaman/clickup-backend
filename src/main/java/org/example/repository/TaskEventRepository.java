package org.example.repository;

import org.example.entity.TaskEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TaskEventRepository extends JpaRepository<TaskEvent, Long> {

    boolean existsByIdempotencyKey(String idempotencyKey);

    boolean existsByHistoryItemId(String historyItemId);

    Optional<TaskEvent> findByHistoryItemId(String historyItemId);

    @Query("SELECT e FROM TaskEvent e WHERE e.task.id = :taskId ORDER BY e.timestamp ASC")
    List<TaskEvent> findByTaskIdOrderByTimestampAsc(@Param("taskId") String taskId);

    @Query("SELECT e FROM TaskEvent e WHERE e.task.id IN :taskIds ORDER BY e.timestamp ASC")
    List<TaskEvent> findByTaskIdIn(@Param("taskIds") List<String> taskIds);
}
