package com.karan.ev_charging_backend.station;

import com.karan.ev_charging_backend.discovery.StationSource;
import com.karan.ev_charging_backend.publicstation.PublicChargingStation;
import com.karan.ev_charging_backend.publicstation.PublicChargingStationRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StationBookingSettingsService {

    private final StationRepository stationRepository;
    private final PublicChargingStationRepository publicStationRepository;

    public StationBookingSettingsService(
            StationRepository stationRepository,
            PublicChargingStationRepository publicStationRepository) {
        this.stationRepository = stationRepository;
        this.publicStationRepository = publicStationRepository;
    }

    @Transactional
    public boolean setBookingEnabled(StationSource source, Long stationId, boolean enabled) {
        if (source == StationSource.MANAGED) {
            Station station = stationRepository.findByIdForUpdate(stationId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND, "Managed charging station not found."));
            station.setBookingEnabled(enabled);
            return station.isBookingEnabled();
        }

        PublicChargingStation station = publicStationRepository.findByIdForUpdate(stationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Public charging station not found."));
        station.setBookingEnabled(enabled);
        return station.isBookingEnabled();
    }
}
