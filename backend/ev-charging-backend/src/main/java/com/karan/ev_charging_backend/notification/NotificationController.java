
package com.karan.ev_charging_backend.notification;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    private Long authenticatedUserId(Jwt jwt) {
        if (jwt == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Authentication required.");
        }

        Object claim = jwt.getClaim("userId");

        if (!(claim instanceof Number)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid user identity in token.");
        }

        return ((Number) claim).longValue();
    }

    @GetMapping
    public List<Notification> getNotifications(
            @AuthenticationPrincipal Jwt jwt) {
        return notificationService.getUserNotifications(
                authenticatedUserId(jwt));
    }

    @GetMapping("/unread-count")
    public long getUnreadCount(
            @AuthenticationPrincipal Jwt jwt) {
        return notificationService.getUnreadCount(
                authenticatedUserId(jwt));
    }

    @PutMapping("/{id}/read")
    public Notification markAsRead(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        Long userId = authenticatedUserId(jwt);

        return notificationService.markAsRead(id, userId);
    }
}
