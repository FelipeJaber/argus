package br.com.argus.user;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        String email,
        Role role,
        String sector,
        boolean active,
        Instant lastLoginAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(), user.getRole(),
                user.getSector(), user.isActive(), user.getLastLoginAt());
    }
}
