package com.karan.ev_charging_backend.controller;

import com.karan.ev_charging_backend.auth.UserRepository;
import com.karan.ev_charging_backend.auth.UserRole;
import com.karan.ev_charging_backend.booking.BookingRepository;
import com.karan.ev_charging_backend.booking.BookingStatus;
import com.karan.ev_charging_backend.publicstation.PublicChargingStationRepository;
import com.karan.ev_charging_backend.station.StationRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AdminAnalyticsService {

    private static final int REPORT_MONTHS = 12;
    private static final int TOP_PLACES = 10;
    private static final String REVENUE_UNAVAILABLE_REASON =
            "Revenue data unavailable: no payment records are stored.";

    private final PublicChargingStationRepository publicStationRepository;
    private final StationRepository stationRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;

    public AdminAnalyticsService(
            PublicChargingStationRepository publicStationRepository,
            StationRepository stationRepository,
            UserRepository userRepository,
            BookingRepository bookingRepository) {
        this.publicStationRepository = publicStationRepository;
        this.stationRepository = stationRepository;
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
    }

    @Transactional(readOnly = true)
    public AdminAnalyticsResponse getAnalytics() {
        long publicStations = publicStationRepository.count();
        long managedStations = stationRepository.count();
        long totalUsers = userRepository.count();
        long normalUsers = userRepository.countByRole(UserRole.USER);
        long adminCount = userRepository.countByRole(UserRole.ADMIN);
        long totalBookings = bookingRepository.count();
        long requestedBookings = bookingRepository.countByStatus(BookingStatus.REQUESTED);
        long confirmedBookings = bookingRepository.countByStatus(BookingStatus.CONFIRMED);
        long cancelledBookings = bookingRepository.countByStatus(BookingStatus.CANCELLED);

        YearMonth currentMonth = YearMonth.now();
        YearMonth firstMonth = currentMonth.minusMonths(REPORT_MONTHS - 1L);
        List<MonthlyBookingAggregate> aggregates =
                bookingRepository.findMonthlyBookingAggregates(
                        firstMonth.atDay(1),
                        currentMonth.plusMonths(1).atDay(1));
        Map<String, MonthlyBookingAggregate> aggregatesByMonth = new HashMap<>();
        for (MonthlyBookingAggregate aggregate : aggregates) {
            aggregatesByMonth.put(aggregate.getMonth(), aggregate);
        }

        List<MonthlyBookingReport> monthlyReports = new ArrayList<>(REPORT_MONTHS);
        for (int offset = 0; offset < REPORT_MONTHS; offset++) {
            String month = firstMonth.plusMonths(offset).toString();
            MonthlyBookingAggregate aggregate = aggregatesByMonth.get(month);
            monthlyReports.add(new MonthlyBookingReport(
                    month,
                    aggregate == null ? 0 : aggregate.getTotalBookings(),
                    aggregate == null ? 0 : aggregate.getRequestedBookings(),
                    aggregate == null ? 0 : aggregate.getConfirmedBookings(),
                    aggregate == null ? 0 : aggregate.getCancelledBookings(),
                    null,
                    false));
        }

        List<StationPlaceAnalytics> publicCities = publicStationRepository
                .findTopPublicCities(PageRequest.of(0, TOP_PLACES))
                .stream()
                .map(place -> new StationPlaceAnalytics(
                        place.getPlace(), place.getStationCount()))
                .toList();
        List<StationPlaceAnalytics> managedLocations = stationRepository
                .findTopManagedLocations(PageRequest.of(0, TOP_PLACES))
                .stream()
                .map(place -> new StationPlaceAnalytics(
                        place.getPlace(), place.getStationCount()))
                .toList();

        return new AdminAnalyticsResponse(
                publicStations + managedStations,
                publicStations,
                managedStations,
                totalUsers,
                normalUsers,
                adminCount,
                null,
                false,
                totalBookings,
                requestedBookings,
                confirmedBookings,
                cancelledBookings,
                null,
                false,
                REVENUE_UNAVAILABLE_REASON,
                List.copyOf(monthlyReports),
                publicCities,
                managedLocations);
    }
}
