
package com.karan.ev_charging_backend.booking;

import jakarta.persistence.LockModeType;
import com.karan.ev_charging_backend.controller.MonthlyBookingAggregate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findAllByUser_Id(Long userId);

    List<Booking> findAllByOrderByBookingDateDescStartTimeDesc();

    Optional<Booking> findByIdAndUser_Id(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);

    long countByStatus(BookingStatus status);

    @Query(value = """
            SELECT DATE_FORMAT(booking_date, '%Y-%m') AS month,
                   COUNT(*) AS totalBookings,
                   SUM(CASE WHEN status = 'REQUESTED' THEN 1 ELSE 0 END) AS requestedBookings,
                   SUM(CASE WHEN status = 'CONFIRMED' THEN 1 ELSE 0 END) AS confirmedBookings,
                   SUM(CASE WHEN status = 'CANCELLED' THEN 1 ELSE 0 END) AS cancelledBookings
            FROM bookings
            WHERE booking_date >= :startDate AND booking_date < :endDate
            GROUP BY DATE_FORMAT(booking_date, '%Y-%m')
            ORDER BY month
            """, nativeQuery = true)
    List<MonthlyBookingAggregate> findMonthlyBookingAggregates(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("""
        SELECT COUNT(b)
        FROM Booking b
        WHERE b.station.id = :stationId
          AND b.bookingDate = :bookingDate
          AND b.status = :status
          AND b.startTime < :endTime
          AND b.endTime > :startTime
        """)
    long countOverlappingBookings(
            @Param("stationId") Long stationId,
            @Param("bookingDate") LocalDate bookingDate,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("status") BookingStatus status
    );

    @Query("""
        SELECT COUNT(b)
        FROM Booking b
        WHERE b.publicStation.id = :stationId
          AND b.bookingDate = :bookingDate
          AND b.status IN :statuses
          AND b.startTime < :endTime
          AND b.endTime > :startTime
        """)
    long countOverlappingPublicBookings(
            @Param("stationId") Long stationId,
            @Param("bookingDate") LocalDate bookingDate,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("statuses") Collection<BookingStatus> statuses);
}
