package com.karan.ev_charging_backend.auth;

import com.karan.ev_charging_backend.station.Station;
import com.karan.ev_charging_backend.station.StationRepository;
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
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureMockMvc
class AuthBookingFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String email;
    private Station station;
    private Long userId;

    @BeforeEach
    void setUp() {
        email = "e2e-user-" + UUID.randomUUID() + "@example.test";
        station = new Station();
        station.setName("E2E test managed station");
        station.setLocation("Test-only location");
        station.setChargerType("Integration");
        station.setPricePerUnit(10.0);
        station.setAvailable(true);
        station = stationRepository.save(station);
    }

    @AfterEach
    void cleanUp() {
        if (userId != null) {
            jdbcTemplate.update("DELETE FROM notifications WHERE user_id = ?", userId);
            jdbcTemplate.update("DELETE FROM bookings WHERE user_id = ?", userId);
            userRepository.deleteById(userId);
        }
        if (station != null && station.getId() != null
                && stationRepository.existsById(station.getId())) {
            stationRepository.deleteById(station.getId());
        }
    }

    @Test
    void registrationLoginDiscoveryBookingCancellationAndHistoryWorkEndToEnd()
            throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":" E2E Test User ","email":"%s","password":"test-password-123"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("E2E Test User"))
                .andExpect(jsonPath("$.role").value("USER"));

        User registeredUser = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        userId = registeredUser.getId();

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"test-password-123"}
                                """.formatted(email.toUpperCase())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("USER"))
                .andReturn();

        String token = extractJsonString(
                loginResult.getResponse().getContentAsString(), "token");
        assertNotNull(token);

        mockMvc.perform(get("/api/stations/discovery")
                        .param("source", "MANAGED")
                        .param("q", "E2E test managed station"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].bookable").value(true))
                .andExpect(jsonPath("$.content[0].available").value(true));

        LocalDate bookingDate = LocalDate.now().plusDays(30);
        MvcResult bookingResult = mockMvc.perform(post(
                                "/api/bookings/station/{stationId}", station.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"bookingDate":"%s","startTime":"12:00:00","endTime":"13:00:00","customerName":"Untrusted name","status":"CANCELLED"}
                                """.formatted(bookingDate)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("E2E Test User"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andReturn();

        long bookingId = extractJsonLong(
                bookingResult.getResponse().getContentAsString(), "id");

        mockMvc.perform(get("/api/bookings")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(bookingId));

        mockMvc.perform(put("/api/bookings/{id}/cancel", bookingId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/api/bookings/{id}", bookingId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    private String extractJsonString(String json, String key) {
        Matcher matcher = Pattern.compile(
                "\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]+)\"")
                .matcher(json);
        if (!matcher.find()) {
            throw new AssertionError("Response did not contain " + key + ".");
        }
        return matcher.group(1);
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
}
