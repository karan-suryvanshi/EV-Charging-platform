package com.karan.ev_charging_backend.review;

import com.karan.ev_charging_backend.auth.User;
import com.karan.ev_charging_backend.auth.UserRepository;
import com.karan.ev_charging_backend.booking.BookingRepository;
import com.karan.ev_charging_backend.booking.BookingStatus;
import com.karan.ev_charging_backend.station.Station;
import com.karan.ev_charging_backend.station.StationRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserRepository userRepository;
    private final StationRepository stationRepository;
    private final BookingRepository bookingRepository;

    public ReviewService(
            ReviewRepository reviewRepository,
            UserRepository userRepository,
            StationRepository stationRepository,
            BookingRepository bookingRepository) {
        this.reviewRepository = reviewRepository;
        this.userRepository = userRepository;
        this.stationRepository = stationRepository;
        this.bookingRepository = bookingRepository;
    }

    @Transactional(readOnly = true)
    public List<ReviewResponse> getStationReviews(Long stationId) {
        if (!stationRepository.existsById(stationId)) {
            throw new RuntimeException("Station not found.");
        }

        return reviewRepository
                .findAllByStation_IdOrderByCreatedAtDesc(stationId)
                .stream()
                .map(review -> new ReviewResponse(
                        review.getId(),
                        review.getUser().getName(),
                        review.getRating(),
                        review.getComment(),
                        review.getCreatedAt()
                ))
                .toList();
    }

    @Transactional
    public Review addReview(
            Long stationId, Long userId, ReviewRequest request) {

        Station station = stationRepository.findById(stationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Station not found."));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "User not found."));

        boolean hasConfirmedBooking =
                bookingRepository.findAllByUser_Id(userId)
                        .stream()
                        .anyMatch(booking ->
                                booking.getStation().getId().equals(stationId)
                                && booking.getStatus() == BookingStatus.CONFIRMED);

        if (!hasConfirmedBooking) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You need a confirmed booking at this station to review it.");
        }

        if (reviewRepository.existsByUser_IdAndStation_Id(userId, stationId)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "You have already reviewed this station.");
        }

        if (request.getRating() < 1 || request.getRating() > 5) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Rating must be between 1 and 5.");
        }

        if (request.getComment() == null
                || request.getComment().isBlank()
                || request.getComment().length() > 1000) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Comment must contain 1 to 1000 characters.");
        }

        Review review = new Review();
        review.setUser(user);
        review.setStation(station);
        review.setRating(request.getRating());
        review.setComment(request.getComment().trim());

        return reviewRepository.save(review);
    }
}