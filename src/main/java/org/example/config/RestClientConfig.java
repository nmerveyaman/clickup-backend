package org.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    private final ClickUpApiProperties clickUpApiProperties;

    public RestClientConfig(ClickUpApiProperties clickUpApiProperties) {
        this.clickUpApiProperties = clickUpApiProperties;
    }

    @Bean
    public RestClient clickUpRestClient() {
        return RestClient.builder()
                .baseUrl(clickUpApiProperties.baseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
