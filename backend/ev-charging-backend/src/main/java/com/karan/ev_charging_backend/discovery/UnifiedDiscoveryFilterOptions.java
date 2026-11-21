package com.karan.ev_charging_backend.discovery;

import java.util.List;

public record UnifiedDiscoveryFilterOptions(
        List<String> states,
        List<String> districts,
        List<String> cities,
        List<String> chargerTypes,
        List<String> operators,
        List<String> sources
) {
}
