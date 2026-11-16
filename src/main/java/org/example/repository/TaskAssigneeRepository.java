package org.example.repository;

import org.example.entity.TaskAssignee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TaskAssigneeRepository extends JpaRepository<TaskAssignee, Long> {

    List<TaskAssignee> findByTaskId(String taskId);

    List<TaskAssignee> findByUserId(Long userId);

    @Query("SELECT a FROM TaskAssignee a WHERE a.task.id IN :taskIds")
    List<TaskAssignee> findByTaskIdIn(@Param("taskIds") List<String> taskIds);
}
