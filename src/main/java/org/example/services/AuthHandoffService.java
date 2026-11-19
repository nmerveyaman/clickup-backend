package org.example.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.example.entity.AuthHandoffTicket;
import org.example.repository.AuthHandoffTicketRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthHandoffService {
    private static final long TICKET_LIFETIME_MILLIS = 30_000L;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AuthHandoffTicketRepository ticketRepository;

    @Transactional
    public String create(String jwt) {
        byte[] randomCode = new byte[32];
        SECURE_RANDOM.nextBytes(randomCode);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(randomCode);

        AuthHandoffTicket ticket = new AuthHandoffTicket();
        ticket.setCodeHash(hash(code));
        ticket.setAccessToken(jwt);
        ticket.setExpiresAt(System.currentTimeMillis() + TICKET_LIFETIME_MILLIS);
        ticketRepository.save(ticket);
        return code;
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public String consume(String code) {
        if (code == null || code.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Authentication code is required");
        }

        AuthHandoffTicket ticket = ticketRepository.findByCodeHashForUpdate(hash(code))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authentication code"));
        ticketRepository.delete(ticket);
        if (ticket.getExpiresAt() < System.currentTimeMillis()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication code has expired");
        }
        return ticket.getAccessToken();
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
