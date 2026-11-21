package com.karan.ev_charging_backend.booking;

import com.karan.ev_charging_backend.auth.UserRepository;
import com.karan.ev_charging_backend.notification.NotificationService;
import com.karan.ev_charging_backend.station.StationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BookingServiceTest {

    @Test
    void rejectsBookingForEarlierTimeToday() {
        BookingService service = new BookingService(
                mock(BookingRepository.class),
                mock(StationRepository.class),
                mock(UserRepository.class),
                mock(NotificationService.class));
        Booking booking = new Booking();
        booking.setBookingDate(LocalDate.now());
        booking.setStartTime(LocalTime.MIDNIGHT);
        booking.setEndTime(LocalTime.of(0, 1));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.createBooking(booking, 1L, 1L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void rejectsBookingOnEarlierDate() {
        BookingService service = new BookingService(
                mock(BookingRepository.class),
                mock(StationRepository.class),
                mock(UserRepository.class),
                mock(NotificationService.class));
        Booking booking = new Booking();
        booking.setBookingDate(LocalDate.now().minusDays(1));
        booking.setStartTime(LocalTime.NOON);
        booking.setEndTime(LocalTime.of(13, 0));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.createBooking(booking, 1L, 1L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void approvesRequestedBookingAndNotifiesUserWithoutAvailabilityClaim() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        NotificationService notificationService = mock(NotificationService.class);
        Booking request = new Booking();
        request.setStatus(BookingStatus.REQUESTED);
        when(bookingRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(request));
        when(bookingRepository.save(request)).thenReturn(request);
        BookingService service = new BookingService(
                bookingRepository,
                mock(StationRepository.class),
                mock(UserRepository.class),
                notificationService);

        Booking approved = service.updateBookingStatus(12L, BookingStatus.CONFIRMED);

        assertEquals(BookingStatus.CONFIRMED, approved.getStatus());
        verify(notificationService).createNotification(
                null,
                "Your booking request has been approved by an administrator. Availability is not verified automatically.",
                "BOOKING_STATUS_UPDATED");
    }

    @Test
    void rejectsAdminDecisionsForNonRequestedBookings() {
        BookingRepository bookingRepository = mock(BookingRepository.class);
        Booking confirmed = new Booking();
        confirmed.setStatus(BookingStatus.CONFIRMED);
        when(bookingRepository.findByIdForUpdate(12L)).thenReturn(Optional.of(confirmed));
        BookingService service = new BookingService(
                bookingRepository,
                mock(StationRepository.class),
                mock(UserRepository.class),
                mock(NotificationService.class));

        ResponseStatusException confirmCancelled = assertThrows(
                ResponseStatusException.class,
                () -> service.updateBookingStatus(12L, BookingStatus.CANCELLED));
        assertEquals(HttpStatus.CONFLICT, confirmCancelled.getStatusCode());

        confirmed.setStatus(BookingStatus.CANCELLED);
        ResponseStatusException cancelConfirmed = assertThrows(
                ResponseStatusException.class,
                () -> service.updateBookingStatus(12L, BookingStatus.CONFIRMED));
        assertEquals(HttpStatus.CONFLICT, cancelConfirmed.getStatusCode());

        ResponseStatusException resetToRequested = assertThrows(
                ResponseStatusException.class,
                () -> service.updateBookingStatus(12L, BookingStatus.REQUESTED));
        assertEquals(HttpStatus.BAD_REQUEST, resetToRequested.getStatusCode());
    }
}
