package org.example.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "clickup.api")
public record ClickUpApiProperties(
        String baseUrl,
        Endpoints endpoints,
        String accessToken,
        String clientId,
        String clientSecret,
        String redirectUri) {

    public record Endpoints(String teams, String spaces, String folders, String spaceList, String tasks) {}
}
