package org.example.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.config.JwtProperties;
import org.example.entity.User;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties jwtProperties;

    private SecretKey getSigningKey() {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(jwtProperties.secret());
        } catch (Exception e) {
            keyBytes = jwtProperties.secret().getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length < 32) {
            keyBytes = jwtProperties.secret().getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 256 bits of key material");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(User user) {
        return generateToken(user, null);
    }

    public String generateToken(User user, String workspaceId) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("userId", user.getId());
        extraClaims.put("username", user.getUsername());
        extraClaims.put("email", user.getEmail());
        extraClaims.put("role", user.getRole());
        if (workspaceId != null) {
            extraClaims.put("workspaceId", workspaceId);
        }

        return generateToken(extraClaims, String.valueOf(user.getId()));
    }

    public String generateToken(Map<String, Object> extraClaims, String subject) {
        if (jwtProperties.expirationMs() <= 0) {
            throw new IllegalStateException("JWT_EXPIRATION_MS must be greater than zero");
        }
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .claims(extraClaims)
                .subject(subject)
                .issuedAt(new Date(now))
                .expiration(new Date(now + jwtProperties.expirationMs()))
                .signWith(getSigningKey())
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(token).getPayload();
            Object userId = claims.get("userId");
            return claims.getExpiration() != null
                    && userId != null
                    && String.valueOf(userId).equals(claims.getSubject())
                    && Long.parseLong(String.valueOf(userId)) > 0;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Geçersiz JWT Token: {}", e.getMessage());
            return false;
        }
    }

    public Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public Long extractUserId(String token) {
        Claims claims = extractAllClaims(token);
        Object userIdObj = claims.get("userId");
        if (userIdObj != null) {
            return Long.valueOf(userIdObj.toString());
        }
        return Long.valueOf(claims.getSubject());
    }

    public String extractWorkspaceId(String token) {
        Claims claims = extractAllClaims(token);
        Object wsId = claims.get("workspaceId");
        return wsId != null ? wsId.toString() : null;
    }

    public String extractRole(String token) {
        return extractAllClaims(token).get("role", String.class);
    }

    public String extractEmail(String token) {
        return extractAllClaims(token).get("email", String.class);
    }

    public String extractUsername(String token) {
        return extractAllClaims(token).get("username", String.class);
    }
}
