package com.karan.ev_charging_backend.publicstation;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.AssertTrue;

import java.math.BigDecimal;

public record PublicStationUpdateRequest(
        @Size(max = 255) String cpoName,
        @Size(max = 255) String ownership,
        @Size(max = 255) String state,
        @Size(max = 255) String district,
        @Size(max = 255) String cityVillage,
        @Size(max = 4000) String location,
        @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        @Size(max = 255) String chargerType,
        @DecimalMin("0.0") BigDecimal ratingKw,
        @Size(max = 255) String secondaryRatingKw,
        @PositiveOrZero Integer connectorCount) {

    @AssertTrue(message = "Latitude and longitude must both be provided or both be omitted.")
    public boolean isCoordinatePairComplete() {
        return (latitude == null) == (longitude == null);
    }
}
