package org.example.repository;

import java.util.List;
import java.util.Optional;
import org.example.entity.UserTokens;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface UserTokensRepository extends JpaRepository<UserTokens, Long> {
    @Override
    @EntityGraph(attributePaths = {"user", "workspace"})
    List<UserTokens> findAll();

    Optional<UserTokens> findByUserIdAndWorkspaceId(Long userId, String workspaceId);

    @EntityGraph(attributePaths = {"workspace"})
    List<UserTokens> findByUserId(Long userId);

    @Transactional
    void deleteByUserId(Long userId);
}
