package com.karan.ev_charging_backend.controller;

public interface MonthlyBookingAggregate {

    String getMonth();

    Long getTotalBookings();

    Long getRequestedBookings();

    Long getConfirmedBookings();

    Long getCancelledBookings();
}
