package com.karan.ev_charging_backend.publicstation;

import java.util.List;

public record PublicStationFilterOptions(
        List<String> states,
        List<String> districts,
        List<String> cities,
        List<String> chargerTypes,
        List<String> operators) {
}
