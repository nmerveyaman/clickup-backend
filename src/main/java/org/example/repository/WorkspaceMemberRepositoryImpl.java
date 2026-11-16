package org.example.repository;

import org.springframework.stereotype.Repository;

@Repository
public class WorkspaceMemberRepositoryImpl implements WorkspaceMemberRepositoryCustom {
    @Override
    public boolean existsByWorkspaceIdAndUserId(Long workspaceId, Long userId) {
        // Implementation for checking if a user is a member of a workspace
        return false;
    }
}
