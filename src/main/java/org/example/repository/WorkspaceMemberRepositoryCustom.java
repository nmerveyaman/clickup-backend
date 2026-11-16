package org.example.repository;

import org.example.entity.WorkspaceMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WorkspaceMemberRepositoryCustom  {
    boolean existsByWorkspaceIdAndUserId(Long workspaceId, Long userId);
}
