
package com.karan.ev_charging_backend.booking;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    private Long authenticatedUserId(Jwt jwt) {
        if (jwt == null) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Authentication required.");
        }

        Object claim = jwt.getClaim("userId");

        if (!(claim instanceof Number)) {
            throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "Invalid user identity in token.");
        }

        return ((Number) claim).longValue();
    }

    @GetMapping("/admin/all")
    public ResponseEntity<List<Booking>> getAllBookingsForAdmin() {
        return ResponseEntity.ok(
            bookingService.getAllBookingsForAdmin()
        );
    }

    @PutMapping("/admin/{id}/status")
    public ResponseEntity<Booking> updateBookingStatus(
            @PathVariable Long id,
            @RequestBody java.util.Map<String, String> request) {

        String statusValue = request.get("status");

        if (statusValue == null || statusValue.isBlank()) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Booking status is required."
            );
        }

        BookingStatus status;

        try {
            status = BookingStatus.valueOf(
                statusValue.trim().toUpperCase()
            );
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Status must be REQUESTED, CONFIRMED, or CANCELLED."
            );
        }

        return ResponseEntity.ok(
            bookingService.updateBookingStatus(id, status)
        );
    }

    @PostMapping("/station/{stationId}")
    public ResponseEntity<Booking> createBooking(
            @PathVariable Long stationId,
            @RequestBody Booking booking,
            @AuthenticationPrincipal Jwt jwt) {

        Booking saved = bookingService.createBooking(
            booking, stationId, authenticatedUserId(jwt));

        return ResponseEntity.ok(saved);
    }

    @PostMapping("/public-station/{stationId}")
    public ResponseEntity<Booking> createPublicBooking(
            @PathVariable Long stationId,
            @RequestBody Booking booking,
            @AuthenticationPrincipal Jwt jwt) {
        Booking saved = bookingService.createPublicBooking(
                booking, stationId, authenticatedUserId(jwt));
        return ResponseEntity.ok(saved);
    }

    @GetMapping
    public ResponseEntity<List<Booking>> getAllBookings(
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(
            bookingService.getAllBookings(authenticatedUserId(jwt)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Booking> getBookingById(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(
            bookingService.getBookingById(id, authenticatedUserId(jwt)));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<Booking> cancelBooking(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        return ResponseEntity.ok(
            bookingService.cancelBooking(id, authenticatedUserId(jwt)));
    }
}
