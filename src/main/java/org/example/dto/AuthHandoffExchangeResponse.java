package org.example.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AuthHandoffExchangeResponse(@JsonProperty("access_token") String accessToken) {}
