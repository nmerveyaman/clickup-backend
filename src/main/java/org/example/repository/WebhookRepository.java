package org.example.repository;

import java.util.Optional;
import org.example.entity.Webhook;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WebhookRepository extends JpaRepository<Webhook, String> {
    @EntityGraph(attributePaths = {"workspace", "user"})
    Optional<Webhook> findFirstByWorkspaceId(String workspaceId);
}
