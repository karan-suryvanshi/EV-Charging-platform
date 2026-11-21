package com.karan.ev_charging_backend.controller;

import jakarta.validation.constraints.NotNull;

public record BookingEnabledRequest(@NotNull Boolean bookingEnabled) {
}
