package com.karan.ev_charging_backend.publicstation;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PublicStationAdminService {

    private final PublicChargingStationRepository repository;

    public PublicStationAdminService(PublicChargingStationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void update(long id, PublicStationUpdateRequest request) {
        PublicChargingStation station = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Public station not found."));

        station.setCpoName(request.cpoName());
        station.setOwnership(request.ownership());
        station.setState(request.state());
        station.setDistrict(request.district());
        station.setCityVillage(request.cityVillage());
        station.setLocation(request.location());
        station.setLatitude(request.latitude());
        station.setLongitude(request.longitude());
        station.setChargerType(request.chargerType());
        station.setRatingKw(request.ratingKw());
        station.setSecondaryRatingKw(request.secondaryRatingKw());
        station.setConnectorCount(request.connectorCount());
        repository.save(station);
    }

    @Transactional
    public void delete(long id) {
        PublicChargingStation station = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Public station not found."));

        try {
            repository.delete(station);
            repository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This public station has dependent records and cannot be deleted.",
                    exception);
        }
    }
}
