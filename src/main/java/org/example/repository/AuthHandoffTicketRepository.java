package org.example.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.example.entity.AuthHandoffTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthHandoffTicketRepository extends JpaRepository<AuthHandoffTicket, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ticket FROM AuthHandoffTicket ticket WHERE ticket.codeHash = :codeHash")
    Optional<AuthHandoffTicket> findByCodeHashForUpdate(@Param("codeHash") String codeHash);
}
