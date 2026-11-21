
package com.karan.ev_charging_backend.auth;

public record ProfileResponse(
        Long id,
        String name,
        String email
) {
    public static ProfileResponse from(User user) {
        return new ProfileResponse(
                user.getId(),
                user.getName(),
                user.getEmail()
        );
    }
}
