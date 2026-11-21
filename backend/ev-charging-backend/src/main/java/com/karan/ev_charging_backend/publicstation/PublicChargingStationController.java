
package com.karan.ev_charging_backend.publicstation;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public-stations")
public class PublicChargingStationController {

    private final PublicChargingStationRepository repository;
    private final PublicStationAdminService adminService;

    public PublicChargingStationController(
            PublicChargingStationRepository repository,
            PublicStationAdminService adminService) {
        this.repository = repository;
        this.adminService = adminService;
    }

    @PutMapping("/{id}")
    public ResponseEntity<Void> updatePublicStation(
            @PathVariable long id,
            @Valid @RequestBody PublicStationUpdateRequest request) {
        adminService.update(id, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePublicStation(@PathVariable long id) {
        adminService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public Page<PublicChargingStation> getPublicStations(
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "district", required = false) String district,
            @RequestParam(name = "city", required = false) String city,
            @RequestParam(name = "chargerType", required = false) String chargerType,
            @RequestParam(name = "operator", required = false) String operator,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "page", defaultValue = "0") String page,
            @RequestParam(name = "size", defaultValue = "20") String size) {

        int safePage = Math.max(parseInteger(page, 0), 0);
        int safeSize = Math.min(Math.max(parseInteger(size, 20), 1), 100);

        Pageable pageable = PageRequest.of(
                safePage,
                safeSize,
                Sort.by(Sort.Direction.ASC, "id"));

        return repository.searchPublicStations(
                blankToNull(state),
                blankToNull(district),
                blankToNull(city),
                blankToNull(chargerType),
                blankToNull(operator),
                blankToNull(q),
                pageable);
    }

    @GetMapping("/filters")
    public PublicStationFilterOptions getFilterOptions(
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "district", required = false) String district) {
        String normalizedState = blankToNull(state);
        String normalizedDistrict = blankToNull(district);

        return new PublicStationFilterOptions(
                repository.findDistinctStates(),
                repository.findDistinctDistricts(normalizedState),
                repository.findDistinctCities(normalizedState, normalizedDistrict),
                repository.findDistinctChargerTypes(),
                repository.findDistinctOperators());
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.length() > 100 ? trimmed.substring(0, 100) : trimmed;
    }

    private int parseInteger(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
