package org.example.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "auth_handoff_tickets")
@Getter
@Setter
@NoArgsConstructor
public class AuthHandoffTicket {
    @Id
    @Column(name = "code_hash", length = 64)
    private String codeHash;

    @Column(name = "access_token", nullable = false, columnDefinition = "TEXT")
    private String accessToken;

    @Column(name = "expires_at", nullable = false)
    private long expiresAt;
}
