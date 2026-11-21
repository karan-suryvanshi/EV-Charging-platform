package com.karan.ev_charging_backend.station;

import com.karan.ev_charging_backend.controller.StationPlaceCount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.util.List;
import java.util.Optional;

public interface StationRepository extends JpaRepository<Station, Long> {

    @Query(value = """
            SELECT MIN(TRIM(location)) AS place, COUNT(*) AS stationCount
            FROM station
            WHERE location IS NOT NULL AND TRIM(location) <> ''
            GROUP BY LOWER(TRIM(location))
            ORDER BY stationCount DESC, place ASC
            """, nativeQuery = true)
    List<StationPlaceCount> findTopManagedLocations(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Station s WHERE s.id = :id")
    Optional<Station> findByIdForUpdate(@Param("id") Long id);

    @Query("""
        SELECT s FROM Station s
        WHERE (:chargerType IS NULL OR LOWER(TRIM(COALESCE(s.chargerType, ''))) = LOWER(TRIM(:chargerType)))
          AND (:available IS NULL OR s.available = :available)
          AND (:bookingEnabled IS NULL OR s.bookingEnabled = :bookingEnabled)
          AND (:state IS NULL OR LOCATE(LOWER(TRIM(:state)), LOWER(COALESCE(s.location, ''))) > 0)
          AND (:district IS NULL OR LOCATE(LOWER(TRIM(:district)), LOWER(COALESCE(s.location, ''))) > 0)
          AND (:city IS NULL OR LOCATE(LOWER(TRIM(:city)), LOWER(COALESCE(s.location, ''))) > 0)
          AND (:term IS NULL
            OR LOCATE(LOWER(:term), LOWER(COALESCE(s.name, ''))) > 0
            OR LOCATE(LOWER(:term), LOWER(COALESCE(s.location, ''))) > 0
            OR LOCATE(LOWER(:term), LOWER(COALESCE(s.chargerType, ''))) > 0)
        ORDER BY s.id ASC
    """)
    Page<Station> searchStations(
            @Param("chargerType") String chargerType,
            @Param("available") Boolean available,
            @Param("bookingEnabled") Boolean bookingEnabled,
            @Param("term") String term,
            @Param("state") String state,
            @Param("district") String district,
            @Param("city") String city,
            Pageable pageable);

    @Query("""
        SELECT s FROM Station s
        WHERE s.latitude IS NOT NULL AND s.longitude IS NOT NULL
          AND (:chargerType IS NULL OR LOWER(TRIM(COALESCE(s.chargerType, ''))) = LOWER(TRIM(:chargerType)))
          AND (:bookingEnabled IS NULL OR s.bookingEnabled = :bookingEnabled)
          AND (:state IS NULL OR LOCATE(LOWER(TRIM(:state)), LOWER(COALESCE(s.location, ''))) > 0)
          AND (:district IS NULL OR LOCATE(LOWER(TRIM(:district)), LOWER(COALESCE(s.location, ''))) > 0)
          AND (:city IS NULL OR LOCATE(LOWER(TRIM(:city)), LOWER(COALESCE(s.location, ''))) > 0)
          AND (:term IS NULL
            OR LOCATE(LOWER(:term), LOWER(COALESCE(s.name, ''))) > 0
            OR LOCATE(LOWER(:term), LOWER(COALESCE(s.location, ''))) > 0)
        ORDER BY s.id ASC
    """)
    Slice<Station> findMapStations(
            @Param("chargerType") String chargerType,
            @Param("term") String term,
            @Param("state") String state,
            @Param("district") String district,
            @Param("city") String city,
            @Param("bookingEnabled") Boolean bookingEnabled,
            Pageable pageable);

    @Query("SELECT DISTINCT TRIM(s.chargerType) FROM Station s WHERE s.chargerType IS NOT NULL AND TRIM(s.chargerType) <> '' ORDER BY TRIM(s.chargerType)")
    List<String> findDistinctChargerTypes();
}
