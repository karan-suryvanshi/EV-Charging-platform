package com.karan.ev_charging_backend.controller;

import com.karan.ev_charging_backend.publicstation.PublicChargingStationRepository;
import com.karan.ev_charging_backend.discovery.StationSource;
import com.karan.ev_charging_backend.station.StationBookingSettingsService;
import com.karan.ev_charging_backend.station.StationRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/stations")
public class AdminStationSummaryController {

    private final PublicChargingStationRepository publicStationRepository;
    private final StationRepository stationRepository;
    private final StationBookingSettingsService bookingSettingsService;

    public AdminStationSummaryController(
            PublicChargingStationRepository publicStationRepository,
            StationRepository stationRepository,
            StationBookingSettingsService bookingSettingsService) {
        this.publicStationRepository = publicStationRepository;
        this.stationRepository = stationRepository;
        this.bookingSettingsService = bookingSettingsService;
    }

    @GetMapping("/summary")
    public ResponseEntity<AdminStationSummary> getSummary() {
        long publicStations = publicStationRepository.count();
        long managedStations = stationRepository.count();

        return ResponseEntity.ok(new AdminStationSummary(
                publicStations + managedStations,
                publicStations,
                managedStations));
    }

    @PutMapping("/{source}/{id}/booking-enabled")
    public ResponseEntity<BookingEnabledResponse> setBookingEnabled(
            @PathVariable StationSource source,
            @PathVariable Long id,
            @Valid @RequestBody BookingEnabledRequest request) {
        boolean enabled = bookingSettingsService.setBookingEnabled(
                source, id, request.bookingEnabled());
        return ResponseEntity.ok(new BookingEnabledResponse(source, id, enabled));
    }
}
