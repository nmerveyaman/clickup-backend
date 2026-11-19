package org.example.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.config.ClickUpApiProperties;
import org.example.dto.AuthHandoffExchangeRequest;
import org.example.dto.AuthHandoffExchangeResponse;
import org.example.dto.AuthResponseDto;
import org.example.dto.AccessTokenResponse;
import org.example.dto.CurrentUserDto;
import org.example.dto.WorkspaceSelectionRequest;
import org.example.entity.User;
import org.example.repository.UserRepository;
import org.example.repository.UserTokensRepository;
import org.example.repository.WorkspaceMemberRepository;
import org.example.services.AuthService;
import org.example.services.AuthHandoffService;
import org.example.security.JwtService;
import org.example.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthHandoffService authHandoffService;
    private final UserRepository userRepository;
    private final UserTokensRepository userTokensRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final JwtService jwtService;
    private final ClickUpApiProperties clickUpApiProperties;

    @Value("${app.frontend-url:http://localhost/dashboard}")
    private String frontendUrl;

    /**
     * Frontend Login butonuna basıldığında ClickUp OAuth izin sayfasına yönlendirir.
     */
    @GetMapping({"/login", "/api/auth/login"})
    public ResponseEntity<Void> redirectToClickUpOAuth() {
        String oauthUrl = UriComponentsBuilder.fromUriString("https://app.clickup.com/api")
                .queryParam("client_id", clickUpApiProperties.clientId())
                .queryParam("redirect_uri", clickUpApiProperties.redirectUri())
                .build()
                .toUriString();

        log.info("Kullanıcı ClickUp OAuth girişine yönlendiriliyor: {}", oauthUrl);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(oauthUrl))
                .build();
    }

    /**
     * ClickUp OAuth callback endpoint'i.
     * Eğer 'code' parametresi varsa OAuth akışını tamamlar ve dashboard'a yönlendirir.
     * Eğer doğrudan tarayıcıdan / adresine girildiyse (code yoksa) bilgi veya durum mesajı döner.
     */
    @GetMapping(value = {"/api/auth/callback", "/api/v1/auth/callback", "/"})
    public ResponseEntity<?> handleOAuthCallback(
            @RequestParam(value = "code", required = false) String code,
            HttpServletRequest request) {

        if (code == null || code.isBlank()) {
            return ResponseEntity.ok("ClickUp Analytics Backend API is running!");
        }

        log.info(
                "HTTP GET {} OAuth callback isteği alındı. Remote IP: {}",
                request.getRequestURI(),
                request.getRemoteAddr());

        // 1. OAuth akışı, senkronizasyon ve JWT üretimini çalıştır
        AuthResponseDto response = authService.oauthCallback(code, request);

        String handoffCode = authHandoffService.create(response.accessToken());
        String targetUrl = UriComponentsBuilder.fromUriString(frontendUrl)
                .queryParam("code", handoffCode)
                .build()
                .toUriString();

        log.info("OAuth completed; redirecting user to the frontend with a one-time authentication code.");
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(targetUrl))
                .build();
    }

    @org.springframework.web.bind.annotation.PostMapping("/api/v1/auth/exchange")
    public ResponseEntity<AuthHandoffExchangeResponse> exchangeHandoffCode(
            @org.springframework.web.bind.annotation.RequestBody AuthHandoffExchangeRequest request) {
        String jwt = authHandoffService.consume(request.code());
        return ResponseEntity.ok(new AuthHandoffExchangeResponse(jwt));
    }

    @GetMapping("/api/v1/auth/me")
    public ResponseEntity<CurrentUserDto> currentUser(@AuthenticationPrincipal UserPrincipal principal) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Authenticated user no longer exists"));
        return ResponseEntity.ok(new CurrentUserDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getProfilePicture(),
                principal.getWorkspaceId()));
    }

    @org.springframework.web.bind.annotation.PostMapping("/api/v1/auth/workspace")
    public ResponseEntity<AccessTokenResponse> selectWorkspace(
            @org.springframework.web.bind.annotation.RequestBody WorkspaceSelectionRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null || request.workspaceId() == null || request.workspaceId().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "A workspace ID is required");
        }
        String workspaceId = request.workspaceId().trim();
        if (workspaceMemberRepository.findByWorkspaceIdAndUserId(workspaceId, principal.getId()).isEmpty()
                || userTokensRepository.findByUserIdAndWorkspaceId(principal.getId(), workspaceId).isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Workspace is not authorized for this ClickUp account");
        }
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "Authenticated user no longer exists"));
        return ResponseEntity.ok(new AccessTokenResponse(jwtService.generateToken(user, workspaceId)));
    }
}
