package com.karan.ev_charging_backend.discovery;

import com.karan.ev_charging_backend.publicstation.PublicChargingStationRepository;
import com.karan.ev_charging_backend.station.Station;
import com.karan.ev_charging_backend.station.StationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class UnifiedStationDiscoveryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private PublicChargingStationRepository publicStationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Station fixtureStation;
    private String fixtureTerm;
    private String publicFixtureBatchId;

    @BeforeEach
    void setUp() {
        fixtureTerm = "discovery-" + UUID.randomUUID();
        publicFixtureBatchId = "test-" + UUID.randomUUID();
        fixtureStation = new Station();
        fixtureStation.setName("Managed fixture " + fixtureTerm);
        fixtureStation.setLocation("Pune, Pune, Maharashtra");
        fixtureStation.setChargerType("Integration");
        fixtureStation.setPricePerUnit(12.5);
        fixtureStation.setAvailable(true);
        fixtureStation.setLatitude(18.5204);
        fixtureStation.setLongitude(73.8567);
        fixtureStation = stationRepository.save(fixtureStation);

        jdbcTemplate.update("""
                INSERT INTO public_charging_stations (
                    source_record_id, source_page, cpo_name, ownership, state,
                    district, city_village, location, latitude, longitude,
                    charger_type, rating_kw, secondary_rating_kw, connector_count,
                    import_batch_id, source_pdf_record_id, source_warnings
                ) VALUES (900001, 1, 'Test Public Operator', 'Public',
                    'Maharashtra', 'Pune', 'Pune', ?, 18.520400, 73.856700,
                    'CCS-II', 50.00, '50 kW', 1, ?, NULL, NULL)
                """, "Pune test directory " + fixtureTerm, publicFixtureBatchId);
    }

    @AfterEach
    void cleanUp() {
        if (publicFixtureBatchId != null) {
            jdbcTemplate.update(
                    "DELETE FROM public_charging_stations WHERE import_batch_id = ?",
                    publicFixtureBatchId);
        }
        if (fixtureStation != null) {
            stationRepository.deleteById(fixtureStation.getId());
        }
    }

    @Test
    void combinedSearchPreservesSourceIdentityAndBookingEligibility() throws Exception {
        mockMvc.perform(get("/api/stations/discovery")
                        .param("source", "MANAGED")
                        .param("q", fixtureTerm)
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].source").value("MANAGED"))
                .andExpect(jsonPath("$.content[0].id").value("MANAGED-" + fixtureStation.getId()))
                .andExpect(jsonPath("$.content[0].internalId").value(fixtureStation.getId()))
                .andExpect(jsonPath("$.content[0].bookable").value(true))
                .andExpect(jsonPath("$.content[0].available").value(true));

        mockMvc.perform(get("/api/stations/discovery")
                        .param("source", "PUBLIC")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(greaterThan(0)))
                .andExpect(jsonPath("$.content[0].id").value(org.hamcrest.Matchers.startsWith("PUBLIC-")))
                .andExpect(jsonPath("$.content[0].source").value("PUBLIC_DIRECTORY"))
                .andExpect(jsonPath("$.content[0].internalId").value(notNullValue()))
                .andExpect(jsonPath("$.content[0].bookingEnabled").value(true))
                .andExpect(jsonPath("$.content[0].bookable").value(true))
                .andExpect(jsonPath("$.content[0].available").value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(get("/api/stations/discovery")
                        .param("source", "PUBLIC")
                        .param("bookableOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        Long publicFixtureId = jdbcTemplate.queryForObject(
                "SELECT id FROM public_charging_stations WHERE import_batch_id = ?",
                Long.class,
                publicFixtureBatchId);
        jdbcTemplate.update(
                "UPDATE public_charging_stations SET booking_enabled = b'0' WHERE id = ?",
                publicFixtureId);
        mockMvc.perform(get("/api/stations/discovery")
                        .param("source", "PUBLIC")
                        .param("q", fixtureTerm)
                        .param("bookingEnabled", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].bookingEnabled").value(false))
                .andExpect(jsonPath("$.content[0].bookable").value(false));
        mockMvc.perform(get("/api/stations/discovery")
                        .param("source", "PUBLIC")
                        .param("q", fixtureTerm)
                        .param("bookingEnabled", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        MvcResult all = mockMvc.perform(get("/api/stations/discovery").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].source").value("MANAGED"))
                .andReturn();
        assertEquals(stationRepository.count() + publicStationRepository.count(), totalElements(all));
    }

    @Test
    void locationFiltersPaginationAndMapLimitsAreAppliedServerSide() throws Exception {
        mockMvc.perform(get("/api/stations/discovery")
                        .param("source", "MANAGED")
                        .param("state", "Maharashtra")
                        .param("district", "Pune")
                        .param("city", "Pune")
                        .param("q", fixtureTerm)
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/stations/discovery")
                        .param("source", "MANAGED")
                        .param("page", String.valueOf(Integer.MAX_VALUE))
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value((int) stationRepository.count()))
                .andExpect(jsonPath("$.content").isEmpty());

        mockMvc.perform(get("/api/stations/discovery/map")
                        .param("source", "MANAGED")
                        .param("state", "Maharashtra")
                        .param("district", "Pune")
                        .param("city", "Pune")
                        .param("q", fixtureTerm)
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].source").value("MANAGED"));

        mockMvc.perform(get("/api/stations/discovery/map")
                        .param("source", "PUBLIC")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThan(0)))
                .andExpect(jsonPath("$.length()").value(lessThanOrEqualTo(2)))
                .andExpect(jsonPath("$[0].source").value("PUBLIC_DIRECTORY"))
                .andExpect(jsonPath("$[0].bookingEnabled").value(true))
                .andExpect(jsonPath("$[0].bookable").value(true))
                .andExpect(jsonPath("$[0].available").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void filterOptionsRemainDatabaseBacked() throws Exception {
        mockMvc.perform(get("/api/stations/discovery/filters")
                        .param("state", "Maharashtra"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.states").isArray())
                .andExpect(jsonPath("$.districts").isArray())
                .andExpect(jsonPath("$.cities").isArray())
                .andExpect(jsonPath("$.sources[0]").value("ALL"))
                .andExpect(jsonPath("$.sources[1]").value("MANAGED"))
                .andExpect(jsonPath("$.sources[2]").value("PUBLIC"));
    }

    @Test
    void publicStateAndCityFiltersMatchTheTestDirectoryFixture() throws Exception {
        MvcResult filtered = mockMvc.perform(get("/api/stations/discovery")
                        .param("source", "PUBLIC")
                        .param("state", "Maharashtra")
                        .param("city", "Pune")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].source").value("PUBLIC_DIRECTORY"))
                .andReturn();
        assertTrue(totalElements(filtered) > 0);
    }

    private long totalElements(MvcResult result) throws Exception {
        String response = result.getResponse().getContentAsString();
        Matcher matcher = Pattern.compile("\"totalElements\"\\s*:\\s*(\\d+)").matcher(response);
        assertTrue(matcher.find(), "The response should include totalElements.");
        return Long.parseLong(matcher.group(1));
    }
}
