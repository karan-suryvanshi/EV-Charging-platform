
package com.karan.ev_charging_backend.publicstation;

import com.karan.ev_charging_backend.controller.StationPlaceCount;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PublicChargingStationRepository
        extends JpaRepository<PublicChargingStation, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT station FROM PublicChargingStation station WHERE station.id = :id")
    java.util.Optional<PublicChargingStation> findByIdForUpdate(@Param("id") Long id);

    @Query(value = """
            SELECT MIN(TRIM(city_village)) AS place, COUNT(*) AS stationCount
            FROM public_charging_stations
            WHERE city_village IS NOT NULL AND TRIM(city_village) <> ''
            GROUP BY LOWER(TRIM(city_village))
            ORDER BY stationCount DESC, place ASC
            """, nativeQuery = true)
    List<StationPlaceCount> findTopPublicCities(Pageable pageable);

    @Query(value = """
            SELECT station FROM PublicChargingStation station
            WHERE (:state IS NULL OR LOWER(TRIM(COALESCE(station.state, ''))) = LOWER(TRIM(:state)))
              AND (:district IS NULL OR LOWER(TRIM(COALESCE(station.district, ''))) = LOWER(TRIM(:district)))
              AND (:city IS NULL OR LOWER(TRIM(COALESCE(station.cityVillage, ''))) = LOWER(TRIM(:city)))
              AND (:chargerType IS NULL OR LOWER(TRIM(COALESCE(station.chargerType, ''))) = LOWER(TRIM(:chargerType)))
              AND (:operator IS NULL OR LOWER(TRIM(COALESCE(station.cpoName, ''))) = LOWER(TRIM(:operator)))
              AND (:bookingEnabled IS NULL OR station.bookingEnabled = :bookingEnabled)
              AND (:term IS NULL
                OR LOCATE(LOWER(:term), LOWER(COALESCE(station.cpoName, ''))) > 0
                OR LOCATE(LOWER(:term), LOWER(COALESCE(station.ownership, ''))) > 0
                OR LOCATE(LOWER(:term), LOWER(COALESCE(station.state, ''))) > 0
                OR LOCATE(LOWER(:term), LOWER(COALESCE(station.district, ''))) > 0
                OR LOCATE(LOWER(:term), LOWER(COALESCE(station.cityVillage, ''))) > 0
                OR LOCATE(LOWER(:term), LOWER(COALESCE(station.location, ''))) > 0
                OR LOCATE(LOWER(:term), LOWER(COALESCE(station.chargerType, ''))) > 0
                OR LOCATE(LOWER(:term), LOWER(COALESCE(station.sourceWarnings, ''))) > 0)
            """,
            countQuery = """
                    SELECT COUNT(station) FROM PublicChargingStation station
                    WHERE (:state IS NULL OR LOWER(TRIM(COALESCE(station.state, ''))) = LOWER(TRIM(:state)))
                      AND (:district IS NULL OR LOWER(TRIM(COALESCE(station.district, ''))) = LOWER(TRIM(:district)))
                      AND (:city IS NULL OR LOWER(TRIM(COALESCE(station.cityVillage, ''))) = LOWER(TRIM(:city)))
                      AND (:chargerType IS NULL OR LOWER(TRIM(COALESCE(station.chargerType, ''))) = LOWER(TRIM(:chargerType)))
                      AND (:operator IS NULL OR LOWER(TRIM(COALESCE(station.cpoName, ''))) = LOWER(TRIM(:operator)))
                      AND (:bookingEnabled IS NULL OR station.bookingEnabled = :bookingEnabled)
                      AND (:term IS NULL
                        OR LOCATE(LOWER(:term), LOWER(COALESCE(station.cpoName, ''))) > 0
                        OR LOCATE(LOWER(:term), LOWER(COALESCE(station.ownership, ''))) > 0
                        OR LOCATE(LOWER(:term), LOWER(COALESCE(station.state, ''))) > 0
                        OR LOCATE(LOWER(:term), LOWER(COALESCE(station.district, ''))) > 0
                        OR LOCATE(LOWER(:term), LOWER(COALESCE(station.cityVillage, ''))) > 0
                        OR LOCATE(LOWER(:term), LOWER(COALESCE(station.location, ''))) > 0
                        OR LOCATE(LOWER(:term), LOWER(COALESCE(station.chargerType, ''))) > 0
                        OR LOCATE(LOWER(:term), LOWER(COALESCE(station.sourceWarnings, ''))) > 0)
                    """)
    Page<PublicChargingStation> searchPublicStations(
            @Param("state") String state,
            @Param("district") String district,
            @Param("city") String city,
            @Param("chargerType") String chargerType,
            @Param("operator") String operator,
            @Param("term") String term,
            @Param("bookingEnabled") Boolean bookingEnabled,
            Pageable pageable);

    default Page<PublicChargingStation> searchPublicStations(
            String state,
            String district,
            String city,
            String chargerType,
            String operator,
            String term,
            Pageable pageable) {
        return searchPublicStations(
                state, district, city, chargerType, operator, term, null, pageable);
    }

    @Query("SELECT MIN(TRIM(station.state)) FROM PublicChargingStation station "
            + "WHERE station.state IS NOT NULL AND TRIM(station.state) <> '' "
            + "GROUP BY LOWER(TRIM(station.state)) ORDER BY MIN(TRIM(station.state))")
    List<String> findDistinctStates();

    @Query("SELECT MIN(TRIM(station.district)) FROM PublicChargingStation station "
            + "WHERE station.district IS NOT NULL AND TRIM(station.district) <> '' "
            + "AND (:state IS NULL OR LOWER(TRIM(COALESCE(station.state, ''))) = LOWER(TRIM(:state))) "
            + "GROUP BY LOWER(TRIM(station.district)) ORDER BY MIN(TRIM(station.district))")
    List<String> findDistinctDistricts(@Param("state") String state);

    @Query("SELECT MIN(TRIM(station.cityVillage)) FROM PublicChargingStation station "
            + "WHERE station.cityVillage IS NOT NULL AND TRIM(station.cityVillage) <> '' "
            + "AND (:state IS NULL OR LOWER(TRIM(COALESCE(station.state, ''))) = LOWER(TRIM(:state))) "
            + "AND (:district IS NULL OR LOWER(TRIM(COALESCE(station.district, ''))) = LOWER(TRIM(:district))) "
            + "GROUP BY LOWER(TRIM(station.cityVillage)) ORDER BY MIN(TRIM(station.cityVillage))")
    List<String> findDistinctCities(
            @Param("state") String state,
            @Param("district") String district);

    @Query("SELECT MIN(TRIM(station.chargerType)) FROM PublicChargingStation station "
            + "WHERE station.chargerType IS NOT NULL AND TRIM(station.chargerType) <> '' "
            + "GROUP BY LOWER(TRIM(station.chargerType)) ORDER BY MIN(TRIM(station.chargerType))")
    List<String> findDistinctChargerTypes();

    @Query("SELECT MIN(TRIM(station.cpoName)) FROM PublicChargingStation station "
            + "WHERE station.cpoName IS NOT NULL AND TRIM(station.cpoName) <> '' "
            + "GROUP BY LOWER(TRIM(station.cpoName)) ORDER BY MIN(TRIM(station.cpoName))")
    List<String> findDistinctOperators();

    @Query("""
            SELECT station FROM PublicChargingStation station
            WHERE station.latitude IS NOT NULL AND station.longitude IS NOT NULL
              AND (:bookingEnabled IS NULL OR station.bookingEnabled = :bookingEnabled)
              AND (:state IS NULL OR LOWER(TRIM(COALESCE(station.state, ''))) = LOWER(TRIM(:state)))
              AND (:district IS NULL OR LOWER(TRIM(COALESCE(station.district, ''))) = LOWER(TRIM(:district)))
              AND (:city IS NULL OR LOWER(TRIM(COALESCE(station.cityVillage, ''))) = LOWER(TRIM(:city)))
              AND (:chargerType IS NULL OR LOWER(TRIM(COALESCE(station.chargerType, ''))) = LOWER(TRIM(:chargerType)))
              AND (:operator IS NULL OR LOWER(TRIM(COALESCE(station.cpoName, ''))) = LOWER(TRIM(:operator)))
              AND (:term IS NULL
                OR LOCATE(LOWER(:term), LOWER(COALESCE(station.cpoName, ''))) > 0
                OR LOCATE(LOWER(:term), LOWER(COALESCE(station.location, ''))) > 0)
            ORDER BY station.id ASC
            """)
    List<PublicChargingStation> findMapPoints(
            @Param("state") String state,
            @Param("district") String district,
            @Param("city") String city,
            @Param("chargerType") String chargerType,
            @Param("operator") String operator,
            @Param("term") String term,
            @Param("bookingEnabled") Boolean bookingEnabled,
            Pageable pageable);

    Page<PublicChargingStation> findByStateContainingIgnoreCase(
            String state, Pageable pageable);

    Page<PublicChargingStation> findByDistrictContainingIgnoreCase(
            String district, Pageable pageable);

    Page<PublicChargingStation> findByCityVillageContainingIgnoreCase(
            String cityVillage, Pageable pageable);

    Page<PublicChargingStation> findByChargerTypeContainingIgnoreCase(
            String chargerType, Pageable pageable);

    List<PublicChargingStation> findByStateIgnoreCase(String state);

    List<PublicChargingStation> findByDistrictIgnoreCase(String district);
}
