package com.karan.ev_charging_backend.discovery;

import com.karan.ev_charging_backend.publicstation.PublicChargingStation;
import com.karan.ev_charging_backend.publicstation.PublicChargingStationRepository;
import com.karan.ev_charging_backend.review.Review;
import com.karan.ev_charging_backend.review.ReviewRepository;
import com.karan.ev_charging_backend.station.Station;
import com.karan.ev_charging_backend.station.StationRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class UnifiedStationDiscoveryService {

    private final StationRepository stationRepository;
    private final PublicChargingStationRepository publicStationRepository;
    private final ReviewRepository reviewRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public UnifiedStationDiscoveryService(
            StationRepository stationRepository,
            PublicChargingStationRepository publicStationRepository,
            ReviewRepository reviewRepository) {
        this.stationRepository = stationRepository;
        this.publicStationRepository = publicStationRepository;
        this.reviewRepository = reviewRepository;
    }

    @Transactional(readOnly = true)
    public Page<UnifiedStationDto> search(
            String state,
            String district,
            String city,
            String chargerType,
            String operator,
            String source,
            Boolean bookableOnly,
            Boolean bookingEnabled,
            String q,
            int page,
            int size) {

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String normSource = normalizeSource(source);
        boolean requireBookable = Boolean.TRUE.equals(bookableOnly);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        boolean managedAllowed = !"PUBLIC".equals(normSource)
                && (operator == null || operator.isBlank() || operator.equalsIgnoreCase("Host Managed"));

        long managedTotal = 0;
        if (managedAllowed) {
            managedTotal = stationRepository.searchStations(
                    blankToNull(chargerType),
                    requireBookable ? Boolean.TRUE : null,
                    requireBookable ? Boolean.TRUE : bookingEnabled,
                    blankToNull(q),
                    blankToNull(state),
                    blankToNull(district),
                    blankToNull(city),
                    PageRequest.of(0, 1)).getTotalElements();
        }

        if ("PUBLIC".equals(normSource) && requireBookable) {
            return new PageImpl<>(List.of(), pageable, 0);
        }

        if ("MANAGED".equals(normSource) || requireBookable) {
            if (!managedAllowed || pageable.getOffset() > Integer.MAX_VALUE) {
                return new PageImpl<>(List.of(), pageable, managedTotal);
            }
            Page<Station> managedPage = stationRepository.searchStations(
                    blankToNull(chargerType),
                    requireBookable ? Boolean.TRUE : null,
                    requireBookable ? Boolean.TRUE : bookingEnabled,
                    blankToNull(q),
                    blankToNull(state),
                    blankToNull(district),
                    blankToNull(city),
                    pageable);
            List<UnifiedStationDto> dtos = managedPage.getContent().stream()
                    .map(this::toManagedDto)
                    .toList();
            return new PageImpl<>(dtos, pageable, managedPage.getTotalElements());
        }

        if ("PUBLIC".equals(normSource)) {
            if (pageable.getOffset() > Integer.MAX_VALUE) {
                long total = countMatchingPublicStations(state, district, city, chargerType, operator, q, bookingEnabled);
                return new PageImpl<>(List.of(), pageable, total);
            }
            Page<PublicChargingStation> publicPage = publicStationRepository.searchPublicStations(
                    blankToNull(state),
                    blankToNull(district),
                    blankToNull(city),
                    blankToNull(chargerType),
                    blankToNull(operator),
                    blankToNull(q),
                    bookingEnabled,
                    pageable);

            List<UnifiedStationDto> dtos = publicPage.getContent().stream()
                    .map(this::toPublicDto)
                    .toList();
            return new PageImpl<>(dtos, pageable, publicPage.getTotalElements());
        }

        // ALL sources: Managed stations featured first, followed by public directory
        long publicTotal = countMatchingPublicStations(state, district, city, chargerType, operator, q, bookingEnabled);
        long grandTotal = managedTotal + publicTotal;

        long overallStart = pageable.getOffset();
        List<UnifiedStationDto> pageItems = new ArrayList<>();

        if (overallStart >= grandTotal || overallStart > Integer.MAX_VALUE) {
            return new PageImpl<>(pageItems, pageable, grandTotal);
        }

        if (overallStart < managedTotal) {
            Page<Station> managedPage = stationRepository.searchStations(
                    blankToNull(chargerType),
                    null,
                    bookingEnabled,
                    blankToNull(q),
                    blankToNull(state),
                    blankToNull(district),
                    blankToNull(city),
                    pageable);
            for (Station station : managedPage.getContent()) {
                pageItems.add(toManagedDto(station));
            }
            int neededFromPublic = safeSize - pageItems.size();
            if (neededFromPublic > 0 && publicTotal > 0) {
                List<PublicChargingStation> publicSlice = fetchPublicSlice(
                        state, district, city, chargerType, operator, q, bookingEnabled, 0, neededFromPublic);
                for (PublicChargingStation pcs : publicSlice) {
                    pageItems.add(toPublicDto(pcs));
                }
            }
        } else {
            long publicOffset = overallStart - managedTotal;
            if (publicOffset < publicTotal && publicOffset <= Integer.MAX_VALUE) {
                List<PublicChargingStation> publicSlice = fetchPublicSlice(
                        state, district, city, chargerType, operator, q, bookingEnabled, (int) publicOffset, safeSize);
                for (PublicChargingStation pcs : publicSlice) {
                    pageItems.add(toPublicDto(pcs));
                }
            }
        }

        return new PageImpl<>(pageItems, pageable, grandTotal);
    }

    @Transactional(readOnly = true)
    public List<MapStationPointDto> findMapPoints(
            String state,
            String district,
            String city,
            String chargerType,
            String operator,
            String source,
            Boolean bookingEnabled,
            String q,
            int limit) {

        int safeLimit = Math.min(Math.max(limit, 1), 100_000);
        String normSource = normalizeSource(source);
        List<MapStationPointDto> points = new ArrayList<>();

        if (!"PUBLIC".equals(normSource) && (operator == null || operator.isBlank() || operator.equalsIgnoreCase("Host Managed"))) {
            Slice<Station> managedPage = stationRepository.findMapStations(
                    blankToNull(chargerType),
                    blankToNull(q),
                    blankToNull(state),
                    blankToNull(district),
                    blankToNull(city),
                    bookingEnabled,
                    PageRequest.of(0, safeLimit));

            for (Station s : managedPage.getContent()) {
                points.add(new MapStationPointDto(
                        "MANAGED-" + s.getId(),
                        s.getId(),
                        StationSource.MANAGED,
                        s.getName(),
                        "Host Managed",
                        s.getLocation(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        s.getLatitude(),
                        s.getLongitude(),
                        s.getChargerType(),
                        s.getPricePerUnit(),
                        s.isAvailable(),
                        s.isAvailable() && s.isBookingEnabled()
                ));
                points.get(points.size() - 1).setBookingEnabled(s.isBookingEnabled());
            }
        }

        if (!"MANAGED".equals(normSource) && points.size() < safeLimit) {
            int remaining = safeLimit - points.size();
            Pageable mapPageable = PageRequest.of(0, remaining);
            List<PublicChargingStation> publicList = publicStationRepository.findMapPoints(
                    blankToNull(state),
                    blankToNull(district),
                    blankToNull(city),
                    blankToNull(chargerType),
                    blankToNull(operator),
                    blankToNull(q),
                    bookingEnabled,
                    mapPageable);

            for (PublicChargingStation pcs : publicList) {
                Double lat = pcs.getLatitude() != null ? pcs.getLatitude().doubleValue() : null;
                Double lon = pcs.getLongitude() != null ? pcs.getLongitude().doubleValue() : null;
                if (lat != null && lon != null) {
                    points.add(new MapStationPointDto(
                            "PUBLIC-" + pcs.getId(),
                            pcs.getId(),
                            StationSource.PUBLIC_DIRECTORY,
                            publicStationName(pcs.getCityVillage()),
                            null,
                            pcs.getLocation(),
                            pcs.getSourceRecordId(),
                            pcs.getSourcePage(),
                            pcs.getSourcePdfRecordId(),
                            pcs.getImportBatchId(),
                            pcs.getRatingKw(),
                            pcs.getConnectorCount(),
                            lat,
                            lon,
                            pcs.getChargerType(),
                            null,
                            null,
                            pcs.isBookingEnabled()
                    ));
                    points.get(points.size() - 1).setBookingEnabled(pcs.isBookingEnabled());
                }
            }
        }

        return points;
    }

    @Transactional(readOnly = true)
    public UnifiedDiscoveryFilterOptions getFilterOptions(String state, String district) {
        String normalizedState = blankToNull(state);
        String normalizedDistrict = blankToNull(district);

        List<String> states = publicStationRepository.findDistinctStates();
        List<String> districts = publicStationRepository.findDistinctDistricts(normalizedState);
        List<String> cities = publicStationRepository.findDistinctCities(normalizedState, normalizedDistrict);

        List<String> chargerTypes = new ArrayList<>();
        for (String ct : stationRepository.findDistinctChargerTypes()) {
            if (ct != null && !ct.isBlank() && !chargerTypes.contains(ct)) {
                chargerTypes.add(ct);
            }
        }
        for (String ct : publicStationRepository.findDistinctChargerTypes()) {
            if (ct != null && !ct.isBlank() && !chargerTypes.contains(ct)) {
                chargerTypes.add(ct);
            }
        }

        List<String> operators = new ArrayList<>();
        operators.add("Host Managed");
        for (String op : publicStationRepository.findDistinctOperators()) {
            if (op != null && !op.isBlank() && !operators.contains(op)) {
                operators.add(op);
            }
        }

        List<String> sources = List.of("ALL", "MANAGED", "PUBLIC");

        return new UnifiedDiscoveryFilterOptions(states, districts, cities, chargerTypes, operators, sources);
    }

    private long countMatchingPublicStations(
            String state, String district, String city, String chargerType, String operator, String term,
            Boolean bookingEnabled) {
        String countJpql = """
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
                """;

        TypedQuery<Long> query = entityManager.createQuery(countJpql, Long.class);
        setPublicQueryParams(query, state, district, city, chargerType, operator, term, bookingEnabled);
        return query.getSingleResult();
    }

    private List<PublicChargingStation> fetchPublicSlice(
            String state, String district, String city, String chargerType, String operator, String term,
            Boolean bookingEnabled, int offset, int limit) {
        String selectJpql = """
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
                ORDER BY station.id ASC
                """;

        TypedQuery<PublicChargingStation> query = entityManager.createQuery(selectJpql, PublicChargingStation.class);
        setPublicQueryParams(query, state, district, city, chargerType, operator, term, bookingEnabled);
        query.setFirstResult(offset);
        query.setMaxResults(limit);
        return query.getResultList();
    }

    private String publicStationName(String city) {
        return city == null || city.isBlank() ? "Public charging station" : "Public charging station - " + city;
    }

    private void setPublicQueryParams(
            TypedQuery<?> query, String state, String district, String city, String chargerType, String operator,
            String term, Boolean bookingEnabled) {
        query.setParameter("state", blankToNull(state));
        query.setParameter("district", blankToNull(district));
        query.setParameter("city", blankToNull(city));
        query.setParameter("chargerType", blankToNull(chargerType));
        query.setParameter("operator", blankToNull(operator));
        query.setParameter("term", blankToNull(term));
        query.setParameter("bookingEnabled", bookingEnabled);
    }

    private UnifiedStationDto toManagedDto(Station station) {
        UnifiedStationDto dto = new UnifiedStationDto();
        dto.setId("MANAGED-" + station.getId());
        dto.setInternalId(station.getId());
        dto.setSource(StationSource.MANAGED);
        dto.setName(station.getName());
        dto.setOperator("Host Managed");
        dto.setLocation(station.getLocation());
        dto.setLatitude(station.getLatitude());
        dto.setLongitude(station.getLongitude());
        dto.setChargerType(station.getChargerType());
        dto.setPricePerUnit(station.getPricePerUnit());
        dto.setRatingKw(null);
        dto.setConnectorCount(1);
        dto.setAvailable(station.isAvailable());
        dto.setBookingEnabled(station.isBookingEnabled());
        dto.setBookable(station.isAvailable() && station.isBookingEnabled());

        if (!station.isBookingEnabled()) {
            dto.setBookingRestrictionReason("Bookings are disabled for this station.");
        } else if (!station.isAvailable()) {
            dto.setBookingRestrictionReason("Station is currently offline or unavailable for booking.");
        }

        List<Review> reviews = reviewRepository.findAllByStation_IdOrderByCreatedAtDesc(station.getId());
        if (!reviews.isEmpty()) {
            double avg = reviews.stream().mapToInt(Review::getRating).average().orElse(0.0);
            dto.setAverageRating(Math.round(avg * 10.0) / 10.0);
            dto.setReviewCount(reviews.size());
        } else {
            dto.setAverageRating(0.0);
            dto.setReviewCount(0);
        }

        return dto;
    }

    private UnifiedStationDto toPublicDto(PublicChargingStation pcs) {
        UnifiedStationDto dto = new UnifiedStationDto();
        dto.setId("PUBLIC-" + pcs.getId());
        dto.setInternalId(pcs.getId());
        dto.setSource(StationSource.PUBLIC_DIRECTORY);
        dto.setName((pcs.getCpoName() != null ? pcs.getCpoName() : "Public EV Station")
                + (pcs.getCityVillage() != null ? " - " + pcs.getCityVillage() : ""));
        dto.setCpoName(pcs.getCpoName());
        dto.setOperator(pcs.getCpoName() != null ? pcs.getCpoName() : "Public Operator");
        dto.setOwnership(pcs.getOwnership());
        dto.setLocation(pcs.getLocation());
        dto.setState(pcs.getState());
        dto.setDistrict(pcs.getDistrict());
        dto.setCityVillage(pcs.getCityVillage());
        dto.setSourceRecordId(pcs.getSourceRecordId());
        dto.setSourcePage(pcs.getSourcePage());
        dto.setSourcePdfRecordId(pcs.getSourcePdfRecordId());
        dto.setImportBatchId(pcs.getImportBatchId());
        dto.setLatitude(pcs.getLatitude() != null ? pcs.getLatitude().doubleValue() : null);
        dto.setLongitude(pcs.getLongitude() != null ? pcs.getLongitude().doubleValue() : null);
        dto.setChargerType(pcs.getChargerType());
        dto.setPricePerUnit(null);
        dto.setRatingKw(pcs.getRatingKw());
        dto.setSecondaryRatingKw(pcs.getSecondaryRatingKw());
        dto.setConnectorCount(pcs.getConnectorCount());
        dto.setAvailable(null);
        dto.setBookingEnabled(pcs.isBookingEnabled());
        dto.setBookable(pcs.isBookingEnabled());
        dto.setBookingRestrictionReason(pcs.isBookingEnabled()
                ? "This will create a booking request; live availability is unverified."
                : "Bookings are disabled for this station.");
        dto.setAverageRating(0.0);
        dto.setReviewCount(0);
        return dto;
    }

    private String normalizeSource(String source) {
        if (source == null || source.isBlank()) return "ALL";
        String s = source.trim().toUpperCase();
        if ("MANAGED".equals(s) || "PUBLIC".equals(s)) return s;
        return "ALL";
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return trimmed.length() > 100 ? trimmed.substring(0, 100) : trimmed;
    }
}
