package org.example.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record StatusDto(
        String id,
        String status,
        @JsonProperty("orderindex") @JsonAlias("orderIndex") Integer orderIndex,
        String color,
        String type) {}
