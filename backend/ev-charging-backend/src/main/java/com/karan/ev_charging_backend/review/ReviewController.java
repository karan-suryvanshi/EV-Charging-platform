package com.karan.ev_charging_backend.review;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
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

    // Public endpoint: get reviews for a station
    @GetMapping("/station/{stationId}")
    public ResponseEntity<List<ReviewResponse>> getStationReviews(
            @PathVariable Long stationId) {

        return ResponseEntity.ok(
                reviewService.getStationReviews(stationId));
    }

    // Authenticated endpoint: submit a review
    @PostMapping("/station/{stationId}")
    public ResponseEntity<ReviewResponse> addReview(
            @PathVariable Long stationId,
            @Valid @RequestBody ReviewRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        Review savedReview = reviewService.addReview(
                stationId,
                authenticatedUserId(jwt),
                request);

        ReviewResponse response = new ReviewResponse(
                savedReview.getId(),
                savedReview.getUser().getName(),
                savedReview.getRating(),
                savedReview.getComment(),
                savedReview.getCreatedAt()
        );

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(response);
    }
}
