package org.example.repository;

import java.util.List;
import java.util.Optional;
import org.example.entity.WorkspaceMember;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkspaceMemberRepository
        extends JpaRepository<WorkspaceMember, Long> {

    @EntityGraph(attributePaths = {"workspace", "user"})
    List<WorkspaceMember> findByUserId(Long userId);

    @EntityGraph(attributePaths = {"workspace", "user"})
    List<WorkspaceMember> findByWorkspaceId(String workspaceId);

    @EntityGraph(attributePaths = {"workspace", "user"})
    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(String workspaceId, Long userId);
}