
package com.karan.ev_charging_backend.station;

import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stations")
public class StationController {

    private final StationService stationService;

    public StationController(StationService stationService) {
        this.stationService = stationService;
    }

    // Add a new station
    @PostMapping
    public ResponseEntity<Station> addStation(
            @Valid @RequestBody Station station) {
        Station savedStation = stationService.addStation(station);
        return ResponseEntity.ok(savedStation);
    }

    // Get all stations
    @GetMapping
    public ResponseEntity<List<Station>> getAllStations() {
        return ResponseEntity.ok(stationService.getAllStations());
    }

    // Get station by ID
    @GetMapping("/{id}")
    public ResponseEntity<Station> getStationById(
            @PathVariable Long id) {
        return stationService.getStationById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Station> updateStation(
            @PathVariable Long id,
            @Valid @RequestBody Station station) {

        Station updatedStation = stationService.updateStation(id, station);
        return ResponseEntity.ok(updatedStation);
    }

    // Delete station by ID
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteStation(@PathVariable Long id) {
        stationService.deleteStation(id);
        return ResponseEntity.noContent().build();
    }
}
