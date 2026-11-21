package com.karan.ev_charging_backend.booking;

import com.karan.ev_charging_backend.auth.User;
import com.karan.ev_charging_backend.auth.UserRepository;
import com.karan.ev_charging_backend.notification.NotificationService;
import com.karan.ev_charging_backend.publicstation.PublicChargingStation;
import com.karan.ev_charging_backend.publicstation.PublicChargingStationRepository;
import com.karan.ev_charging_backend.station.Station;
import com.karan.ev_charging_backend.station.StationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final StationRepository stationRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final PublicChargingStationRepository publicStationRepository;

    public BookingService(
            BookingRepository bookingRepository,
            StationRepository stationRepository,
            UserRepository userRepository,
            NotificationService notificationService) {
        this(bookingRepository, stationRepository, userRepository, notificationService, null);
    }

    @Autowired
    public BookingService(
            BookingRepository bookingRepository,
            StationRepository stationRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            @Autowired(required = false) PublicChargingStationRepository publicStationRepository) {
        this.bookingRepository = bookingRepository;
        this.stationRepository = stationRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.publicStationRepository = publicStationRepository;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Booking createBooking(Booking booking, Long stationId, Long userId) {
        validateBookingTime(booking);
        User user = findUser(userId);

        Station station = stationRepository.findByIdForUpdate(stationId)
            .orElseGet(() -> {
                if (publicStationRepository != null && publicStationRepository.existsById(stationId)) {
                    throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Public directory stations are reference data only and cannot be booked directly. Please choose an application-managed charging station.");
                }
                throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Charging station not found.");
            });

        if (!station.isBookingEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Bookings are disabled for this station.");
        }

        if (!station.isAvailable()) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Charging station is not available.");
        }

        boolean slotTaken = bookingRepository.countOverlappingBookings(
            stationId,
            booking.getBookingDate(),
            booking.getStartTime(),
            booking.getEndTime(),
            BookingStatus.CONFIRMED
        ) > 0;

        if (slotTaken) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "This time slot is already booked.");
        }

        // Owner and customer name come from the authenticated account.
        // Never trust an owner ID or customer name supplied by the client.
        booking.setUser(user);
        booking.setCustomerName(user.getName());
        booking.setStation(station);
        booking.setStatus(BookingStatus.CONFIRMED);

        Booking savedBooking = bookingRepository.save(booking);

        notificationService.createNotification(
                user,
                "Your charging station booking has been confirmed.",
                "BOOKING_CONFIRMED"
        );

        return savedBooking;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Booking createPublicBooking(Booking booking, Long publicStationId, Long userId) {
        validateBookingTime(booking);
        User user = findUser(userId);

        if (publicStationRepository == null) {
            throw new IllegalStateException("Public station booking is not configured.");
        }
        PublicChargingStation station = publicStationRepository.findByIdForUpdate(publicStationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Public charging station not found."));

        if (!station.isBookingEnabled()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Bookings are disabled for this station.");
        }

        boolean slotTaken = bookingRepository.countOverlappingPublicBookings(
                publicStationId,
                booking.getBookingDate(),
                booking.getStartTime(),
                booking.getEndTime(),
                List.of(BookingStatus.REQUESTED, BookingStatus.CONFIRMED)) > 0;
        if (slotTaken) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "A booking request already overlaps this time slot.");
        }

        booking.setUser(user);
        booking.setCustomerName(user.getName());
        booking.setStation(null);
        booking.setPublicStation(station);
        booking.setStatus(BookingStatus.REQUESTED);

        Booking savedBooking = bookingRepository.save(booking);
        notificationService.createNotification(
                user,
                "Your public station booking request was received. Live availability is unverified.",
                "BOOKING_REQUESTED");
        return savedBooking;
    }

    private void validateBookingTime(Booking booking) {
        LocalDateTime now = LocalDateTime.now();
        if (booking.getBookingDate() == null ||
                booking.getStartTime() == null ||
                booking.getEndTime() == null ||
                booking.getBookingDate().isBefore(now.toLocalDate()) ||
                !booking.getStartTime().isBefore(booking.getEndTime()) ||
                LocalDateTime.of(booking.getBookingDate(), booking.getStartTime()).isBefore(now)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid booking date or time.");
        }
    }

    private User findUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED, "User account not found."));
    }

    @Transactional(readOnly = true)
    public List<Booking> getAllBookings(Long userId) {
        return bookingRepository.findAllByUser_Id(userId);
    }

    @Transactional(readOnly = true)
    public List<Booking> getAllBookingsForAdmin() {
        return bookingRepository.findAllByOrderByBookingDateDescStartTimeDesc();
    }

    @Transactional(readOnly = true)
    public Booking getBookingById(Long id, Long userId) {
        return bookingRepository.findByIdAndUser_Id(id, userId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Booking not found."));
    }

    @Transactional
    public Booking cancelBooking(Long id, Long userId) {
        Booking booking = bookingRepository.findByIdAndUser_Id(id, userId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Booking not found."));

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Booking is already cancelled.");
        }

        booking.setStatus(BookingStatus.CANCELLED);

        Booking cancelledBooking = bookingRepository.save(booking);

        notificationService.createNotification(
                booking.getUser(),
                "Your charging station booking has been cancelled.",
                "BOOKING_CANCELLED"
        );

        return cancelledBooking;
    }

    @Transactional
    public Booking updateBookingStatus(Long id, BookingStatus status) {

        if (status != BookingStatus.CONFIRMED && status != BookingStatus.CANCELLED) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Admin can only approve or cancel a requested booking."
            );
        }

        Booking booking = bookingRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Booking not found."
            ));

        if (booking.getStatus() != BookingStatus.REQUESTED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only requested bookings can be approved or cancelled by an administrator.");
        }

        booking.setStatus(status);

        Booking updatedBooking = bookingRepository.save(booking);
        String notificationMessage = status == BookingStatus.CONFIRMED
                ? "Your booking request has been approved by an administrator. Availability is not verified automatically."
                : "Your booking request has been cancelled by an administrator.";

        notificationService.createNotification(
                booking.getUser(),
                notificationMessage,
                "BOOKING_STATUS_UPDATED"
        );

        return updatedBooking;
    }
}
