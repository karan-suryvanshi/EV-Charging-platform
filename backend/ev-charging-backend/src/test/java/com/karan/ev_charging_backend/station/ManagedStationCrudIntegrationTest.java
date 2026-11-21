package com.karan.ev_charging_backend.station;

import com.karan.ev_charging_backend.auth.JwtService;
import com.karan.ev_charging_backend.auth.User;
import com.karan.ev_charging_backend.auth.UserRepository;
import com.karan.ev_charging_backend.auth.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureMockMvc
class ManagedStationCrudIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Station managedStation;
    private User admin;
    private User normalUser;
    private String publicFixtureBatchId;
    private Long createdStationId;

    @BeforeEach
    void setUp() {
        String fixture = UUID.randomUUID().toString();
        admin = userRepository.save(createUser(
                "station-admin-" + fixture + "@example.test", UserRole.ADMIN));
        normalUser = userRepository.save(createUser(
                "station-user-" + fixture + "@example.test", UserRole.USER));

        managedStation = new Station();
        managedStation.setName("Managed CRUD fixture");
        managedStation.setLocation("Managed-only test location");
        managedStation.setChargerType("Integration");
        managedStation.setPricePerUnit(10.0);
        managedStation.setAvailable(true);
        managedStation = stationRepository.save(managedStation);

        publicFixtureBatchId = "test-" + fixture;
        jdbcTemplate.update("""
                INSERT INTO public_charging_stations (
                    id, source_record_id, source_page, source_pdf_record_id,
                    location, state, district, city_village, import_batch_id
                ) VALUES (?, 7311, 42, 991, 'Public reference must remain unchanged',
                    'Maharashtra', 'Pune', 'Pune', ?)
                """, managedStation.getId(), publicFixtureBatchId);
    }

    @AfterEach
    void cleanUp() {
        if (publicFixtureBatchId != null) {
            jdbcTemplate.update(
                    "DELETE FROM public_charging_stations WHERE import_batch_id = ?",
                    publicFixtureBatchId);
        }
        if (createdStationId != null && stationRepository.existsById(createdStationId)) {
            stationRepository.deleteById(createdStationId);
        }
        if (managedStation != null && managedStation.getId() != null
                && stationRepository.existsById(managedStation.getId())) {
            stationRepository.deleteById(managedStation.getId());
        }
        deleteUser(admin);
        deleteUser(normalUser);
    }

    @Test
    void managedCrudIsAdminOnlyAndCannotMutatePublicRows() throws Exception {
        String adminToken = jwtService.generateToken(admin);
        String userToken = jwtService.generateToken(normalUser);
        String updatedPayload = stationPayload("Updated managed fixture");

        mockMvc.perform(options("/api/stations")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Access-Control-Allow-Origin", "http://localhost:5173"));

        mockMvc.perform(post("/api/stations")
                        .contentType(APPLICATION_JSON)
                        .content(stationPayload("Rejected station")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/stations")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(APPLICATION_JSON)
                        .content(stationPayload("Rejected station")))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/stations/{id}", managedStation.getId())
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(APPLICATION_JSON)
                        .content(updatedPayload))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/stations/{id}", managedStation.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/stations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(managedStation.getId()));

        mockMvc.perform(put("/api/stations/{id}", managedStation.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(updatedPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated managed fixture"));
        assertPublicFixtureUnchanged();

        mockMvc.perform(post("/api/stations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(stationPayload("Invalid station")
                                .replace("\"latitude\":18.52", "\"latitude\":91.0")))
                .andExpect(status().isBadRequest());
        assertEquals(1, stationRepository.count());

        String created = mockMvc.perform(post("/api/stations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(stationPayload("Created managed fixture")
                                .replace("{", "{\"id\":" + managedStation.getId() + ",")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Created managed fixture"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        createdStationId = extractJsonLong(created, "id");
        assertNotEquals(managedStation.getId(), createdStationId);

        mockMvc.perform(get("/api/stations/{id}", createdStationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(createdStationId));
        mockMvc.perform(delete("/api/stations/{id}", createdStationId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        assertFalse(stationRepository.existsById(createdStationId));

        mockMvc.perform(delete("/api/stations/{id}", managedStation.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        assertFalse(stationRepository.existsById(managedStation.getId()));
        assertPublicFixtureUnchanged();
    }

    @Test
    void publicCrudIsAdminOnlyAndTargetsOnlyThePublicTable() throws Exception {
        String adminToken = jwtService.generateToken(admin);
        String userToken = jwtService.generateToken(normalUser);
        long publicId = managedStation.getId();
        long siblingPublicId = insertPublicFixture("QA public station to preserve");
        long publicCountBeforeDelete = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public_charging_stations", Long.class);

        String updatePayload = """
                {
                  "cpoName": "Edited QA Public Operator",
                  "ownership": "Public",
                  "state": "Maharashtra",
                  "district": "Pune",
                  "cityVillage": "Edited Pune",
                  "location": "Edited QA public location",
                  "latitude": 18.52,
                  "longitude": 73.85,
                  "chargerType": "CCS-II",
                  "ratingKw": 60.0,
                  "secondaryRatingKw": "60 kW",
                  "connectorCount": 2
                }
                """;

        mockMvc.perform(put("/api/public-stations/{id}", publicId)
                        .contentType(APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/public-stations/{id}", publicId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/public-stations/{id}", publicId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/public-stations/{id}", publicId)
                        .header("Authorization", "Bearer " + jwtService.generateToken(admin))
                        .contentType(APPLICATION_JSON)
                        .content(updatePayload.replace("\"longitude\": 73.85", "\"longitude\": null")))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/public-stations/{id}", publicId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(updatePayload))
                .andExpect(status().isNoContent());

        assertEquals("Edited QA Public Operator", jdbcTemplate.queryForObject(
                "SELECT cpo_name FROM public_charging_stations WHERE id = ?",
                String.class, publicId));
        assertEquals(7311, jdbcTemplate.queryForObject(
                "SELECT source_record_id FROM public_charging_stations WHERE id = ?",
                Integer.class, publicId));
        assertEquals(42, jdbcTemplate.queryForObject(
                "SELECT source_page FROM public_charging_stations WHERE id = ?",
                Integer.class, publicId));
        assertEquals(991, jdbcTemplate.queryForObject(
                "SELECT source_pdf_record_id FROM public_charging_stations WHERE id = ?",
                Integer.class, publicId));
        assertEquals(publicFixtureBatchId, jdbcTemplate.queryForObject(
                "SELECT import_batch_id FROM public_charging_stations WHERE id = ?",
                String.class, publicId));
        assertEquals("Managed CRUD fixture", stationRepository.findById(managedStation.getId())
                .orElseThrow().getName());
        assertEquals("QA public station to preserve", jdbcTemplate.queryForObject(
                "SELECT location FROM public_charging_stations WHERE id = ?",
                String.class, siblingPublicId));

        mockMvc.perform(get("/api/stations/discovery")
                        .param("source", "PUBLIC")
                        .param("q", "Edited QA Public Operator")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value("PUBLIC-" + publicId))
                .andExpect(jsonPath("$.content[0].cpoName").value("Edited QA Public Operator"))
                .andExpect(jsonPath("$.content[0].ownership").value("Public"))
                .andExpect(jsonPath("$.content[0].secondaryRatingKw").value("60 kW"));

        mockMvc.perform(delete("/api/public-stations/{id}", publicId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/public-stations/{id}", publicId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public_charging_stations WHERE id = ?",
                Integer.class, publicId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public_charging_stations WHERE id = ?",
                Integer.class, siblingPublicId));
        assertEquals(publicCountBeforeDelete - 1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public_charging_stations", Long.class));
        assertEquals("Managed CRUD fixture", stationRepository.findById(managedStation.getId())
                .orElseThrow().getName());
        assertEquals(1, stationRepository.count());
    }

    private long insertPublicFixture(String location) {
        String operator = "QA operator " + UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO public_charging_stations (
                    location, state, district, city_village, cpo_name, import_batch_id
                ) VALUES (?, 'Maharashtra', 'Pune', 'Pune', ?, ?)
                """, location, operator, publicFixtureBatchId);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM public_charging_stations WHERE cpo_name = ?",
                Long.class, operator);
    }

    private String stationPayload(String name) {
        return """
                {"name":"%s","location":"Managed-only test location","chargerType":"Integration","pricePerUnit":10.0,"available":true,"latitude":18.52,"longitude":73.85}
                """.formatted(name);
    }

    private void assertPublicFixtureUnchanged() {
        String location = jdbcTemplate.queryForObject(
                "SELECT location FROM public_charging_stations WHERE import_batch_id = ?",
                String.class,
                publicFixtureBatchId);
        assertEquals("Public reference must remain unchanged", location);
    }

    private long extractJsonLong(String json, String key) {
        Matcher matcher = Pattern.compile(
                "\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)")
                .matcher(json);
        if (!matcher.find()) {
            throw new AssertionError("Response did not contain " + key + ".");
        }
        return Long.parseLong(matcher.group(1));
    }

    private User createUser(String email, UserRole role) {
        User user = new User();
        user.setName("Managed station test user");
        user.setEmail(email);
        user.setPassword("integration-test-password");
        user.setRole(role);
        return user;
    }

    private void deleteUser(User user) {
        if (user == null || user.getId() == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM notifications WHERE user_id = ?", user.getId());
        jdbcTemplate.update("DELETE FROM bookings WHERE user_id = ?", user.getId());
        userRepository.deleteById(user.getId());
    }
}
