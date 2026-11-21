package com.karan.ev_charging_backend.station;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class StationService {

    private final StationRepository stationRepository;

    public StationService(StationRepository stationRepository) {
        this.stationRepository = stationRepository;
    }

    // Add a new charging station
    public Station addStation(Station station) {
        station.setId(null);
        return stationRepository.save(station);
    }

    // Get all charging stations
    public List<Station> getAllStations() {
        return stationRepository.findAll();
    }

    // Get a station by ID
    public Optional<Station> getStationById(Long id) {
        return stationRepository.findById(id);
    }

    // Delete a station by ID
    public void deleteStation(Long id) {
        stationRepository.deleteById(id);
    }

    public Station updateStation(Long id, Station updatedStation) {
        Station station = stationRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalArgumentException("Station not found"));

        station.setName(updatedStation.getName());
        station.setLocation(updatedStation.getLocation());
        station.setChargerType(updatedStation.getChargerType());
        station.setPricePerUnit(updatedStation.getPricePerUnit());
        station.setAvailable(updatedStation.isAvailable());
        station.setLatitude(updatedStation.getLatitude());
        station.setLongitude(updatedStation.getLongitude());

        return stationRepository.save(station);
    }
}
