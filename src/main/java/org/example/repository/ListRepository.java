package org.example.repository;

import org.example.entity.Lists;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ListRepository extends JpaRepository<Lists, String> {
    List<Lists> findBySpaceId(String spaceId);

    List<Lists> findByFolderId(String folderId);

    @Query("SELECT l FROM Lists l WHERE l.space.workspace.id = :workspaceId")
    List<Lists> findByWorkspaceId(@Param("workspaceId") String workspaceId);
}
