package com.karan.ev_charging_backend.controller;

public record AdminStationSummary(
        long totalStations,
        long publicStations,
        long managedStations) {
}
