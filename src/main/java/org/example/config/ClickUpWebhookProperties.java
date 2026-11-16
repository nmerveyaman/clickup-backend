package org.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "clickup.webhook")
public record ClickUpWebhookProperties(String secret, String endpoint) {}
