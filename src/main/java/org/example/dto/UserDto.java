package org.example.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public record UserDto(
        Long id,
        String username,
        String email,
        @JsonProperty("profile_picture") @JsonAlias({"profilePicture", "profile_picture"}) String profilePicture,
        @JsonProperty("role_key") @JsonAlias({"roleKey", "role_key"}) String roleKey,
        @JsonProperty("date_invited") String dateInvited,
        @JsonProperty("date_joined") String dateJoined) {

    @JsonProperty("profilePicture")
    public String profilePictureCamel() {
        return profilePicture;
    }
}
