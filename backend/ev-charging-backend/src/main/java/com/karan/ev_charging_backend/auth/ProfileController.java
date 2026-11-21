
package com.karan.ev_charging_backend.auth;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    private Long authenticatedUserId(Jwt jwt) {
        if (jwt == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Authentication required."
            );
        }

        Object claim = jwt.getClaim("userId");

        if (!(claim instanceof Number)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED,
                    "Invalid user identity in token."
            );
        }

        return ((Number) claim).longValue();
    }

    @GetMapping
    public ResponseEntity<ProfileResponse> getProfile(
            @AuthenticationPrincipal Jwt jwt) {

        ProfileResponse profile =
                profileService.getProfile(authenticatedUserId(jwt));

        return ResponseEntity.ok(profile);
    }

    @PutMapping
    public ResponseEntity<ProfileResponse> updateProfile(
            @Valid @RequestBody ProfileUpdateRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        ProfileResponse profile = profileService.updateProfile(
                authenticatedUserId(jwt),
                request
        );

        return ResponseEntity.ok(profile);
    }
}
