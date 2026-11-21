package com.karan.ev_charging_backend.discovery;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stations/discovery")
public class UnifiedStationDiscoveryController {

    private final UnifiedStationDiscoveryService discoveryService;

    public UnifiedStationDiscoveryController(UnifiedStationDiscoveryService discoveryService) {
        this.discoveryService = discoveryService;
    }

    @GetMapping
    public ResponseEntity<Page<UnifiedStationDto>> searchStations(
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "district", required = false) String district,
            @RequestParam(name = "city", required = false) String city,
            @RequestParam(name = "chargerType", required = false) String chargerType,
            @RequestParam(name = "operator", required = false) String operator,
            @RequestParam(name = "source", defaultValue = "ALL") String source,
            @RequestParam(name = "bookableOnly", defaultValue = "false") Boolean bookableOnly,
            @RequestParam(name = "bookingEnabled", required = false) Boolean bookingEnabled,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {

        Page<UnifiedStationDto> result = discoveryService.search(
                state, district, city, chargerType, operator, source, bookableOnly,
                bookingEnabled, q, page, size);

        return ResponseEntity.ok(result);
    }

    @GetMapping("/map")
    public ResponseEntity<List<MapStationPointDto>> getMapPoints(
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "district", required = false) String district,
            @RequestParam(name = "city", required = false) String city,
            @RequestParam(name = "chargerType", required = false) String chargerType,
            @RequestParam(name = "operator", required = false) String operator,
            @RequestParam(name = "source", defaultValue = "ALL") String source,
            @RequestParam(name = "bookingEnabled", required = false) Boolean bookingEnabled,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "limit", defaultValue = "200") int limit) {

        List<MapStationPointDto> points = discoveryService.findMapPoints(
                state, district, city, chargerType, operator, source, bookingEnabled, q, limit);

        return ResponseEntity.ok(points);
    }

    @GetMapping("/filters")
    public ResponseEntity<UnifiedDiscoveryFilterOptions> getFilterOptions(
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "district", required = false) String district) {

        UnifiedDiscoveryFilterOptions options = discoveryService.getFilterOptions(state, district);
        return ResponseEntity.ok(options);
    }
}
