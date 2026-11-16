package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.services.WebhookIngestionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/webhooks", "/api/v1/webhook"})
@RequiredArgsConstructor
public class WebhookController {
    private final WebhookIngestionService webhookIngestionService;

    @PostMapping({"/clickup", ""})
    public ResponseEntity<Void> receiveClickUpWebhook(
            @RequestBody String rawPayload,
            @RequestHeader(name = "X-Signature", required = false) String signature) {
        webhookIngestionService.accept(rawPayload, signature);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
