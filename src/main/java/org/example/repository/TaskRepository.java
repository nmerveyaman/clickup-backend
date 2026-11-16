package org.example.repository;

import org.example.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, String> {

    @Query("SELECT t FROM Task t WHERE t.list.id = :listId")
    List<Task> findByListId(@Param("listId") String listId);

    @Query("SELECT t FROM Task t WHERE t.workspace.id = :workspaceId")
    List<Task> findByWorkspaceId(@Param("workspaceId") String workspaceId);

    @Query("SELECT t FROM Task t WHERE t.parent.id = :parentId")
    List<Task> findByParentId(@Param("parentId") String parentId);

    @Query("SELECT t FROM Task t WHERE t.parent.id IN :parentIds")
    List<Task> findByParentIdIn(@Param("parentIds") List<String> parentIds);

    @Query("SELECT t.id, tag FROM Task t JOIN t.tags tag WHERE t.id IN :taskIds")
    List<Object[]> findTagsByTaskIdIn(@Param("taskIds") List<String> taskIds);

    List<Task> findByStatusName(String statusName);

    @Query("SELECT t FROM Task t WHERE t.creator.id = :creatorId")
    List<Task> findByCreatorId(@Param("creatorId") Long creatorId);

    @Query(value = "SELECT MIN(CAST(date_created AS BIGINT)) FROM tasks WHERE list_id = :listId AND date_created IS NOT NULL AND date_created ~ '^[0-9]+$'", nativeQuery = true)
    Optional<Long> findEffectiveSprintStartDate(@Param("listId") String listId);
}
