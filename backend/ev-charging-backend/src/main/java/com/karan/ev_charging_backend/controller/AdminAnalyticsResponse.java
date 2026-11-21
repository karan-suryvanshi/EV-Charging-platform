package com.karan.ev_charging_backend.controller;

import java.math.BigDecimal;
import java.util.List;

public record AdminAnalyticsResponse(
        long totalStations,
        long publicStations,
        long managedStations,
        long totalUsers,
        long normalUsers,
        long adminCount,
        Long activeUsers,
        boolean activeUsersAvailable,
        long totalBookings,
        long requestedBookings,
        long confirmedBookings,
        long cancelledBookings,
        BigDecimal revenue,
        boolean revenueAvailable,
        String revenueUnavailableReason,
        List<MonthlyBookingReport> monthlyReports,
        List<StationPlaceAnalytics> topPublicCities,
        List<StationPlaceAnalytics> topManagedLocations) {
}
