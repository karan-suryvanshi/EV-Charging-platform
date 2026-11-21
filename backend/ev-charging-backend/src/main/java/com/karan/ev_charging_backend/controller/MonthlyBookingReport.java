package com.karan.ev_charging_backend.controller;

import java.math.BigDecimal;

public record MonthlyBookingReport(
        String month,
        long totalBookings,
        long requestedBookings,
        long confirmedBookings,
        long cancelledBookings,
        BigDecimal revenue,
        boolean revenueAvailable) {
}
