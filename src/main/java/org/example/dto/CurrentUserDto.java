package org.example.dto;

public record CurrentUserDto(
        Long id,
        String username,
        String email,
        String profilePicture,
        String defaultWorkspaceId) {}
