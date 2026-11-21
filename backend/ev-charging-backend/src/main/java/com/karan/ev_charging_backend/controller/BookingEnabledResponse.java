package com.karan.ev_charging_backend.controller;

import com.karan.ev_charging_backend.discovery.StationSource;

public record BookingEnabledResponse(
        StationSource source,
        Long stationId,
        boolean bookingEnabled) {
}
