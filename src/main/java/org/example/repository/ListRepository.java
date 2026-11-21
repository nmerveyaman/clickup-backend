package org.example.repository;

import java.util.List;
import java.util.Optional;
import org.example.entity.Lists;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ListRepository extends JpaRepository<Lists, String> {

    @Override
    @EntityGraph(attributePaths = {"space", "space.workspace", "folder"})
    Optional<Lists> findById(String id);

    @EntityGraph(attributePaths = {"space", "space.workspace", "folder"})
    List<Lists> findBySpaceId(String spaceId);

    @EntityGraph(attributePaths = {"space", "space.workspace", "folder"})
    List<Lists> findByFolderId(String folderId);

    @Query("""
            SELECT l FROM Lists l
            JOIN FETCH l.space s
            JOIN FETCH s.workspace w
            LEFT JOIN FETCH l.folder
            WHERE w.id = :workspaceId
            """)
    List<Lists> findByWorkspaceId(@Param("workspaceId") String workspaceId);
}