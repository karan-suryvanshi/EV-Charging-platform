package com.karan.ev_charging_backend.controller;

import com.karan.ev_charging_backend.auth.JwtService;
import com.karan.ev_charging_backend.auth.User;
import com.karan.ev_charging_backend.auth.UserRepository;
import com.karan.ev_charging_backend.auth.UserRole;
import com.karan.ev_charging_backend.booking.BookingRepository;
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

import java.util.UUID;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureMockMvc
class AdminDashboardDataIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User admin;
    private User normalUser;
    private Station station;
    private String publicFixtureBatchId;
    private String publicFixtureOperator;

    @BeforeEach
    void setUp() {
        String fixture = UUID.randomUUID().toString();
        normalUser = userRepository.save(createUser("dashboard-user-" + fixture + "@example.test", UserRole.USER));
        admin = userRepository.save(createUser("dashboard-admin-" + fixture + "@example.test", UserRole.ADMIN));

        station = new Station();
        station.setName("Dashboard fixture " + fixture);
        station.setLocation("Pune, Maharashtra");
        station.setChargerType("Integration");
        station.setPricePerUnit(10.0);
        station.setAvailable(true);
        station = stationRepository.save(station);

        publicFixtureBatchId = "admin-dashboard-test-" + fixture;
        publicFixtureOperator = "Test Operator " + fixture;
        jdbcTemplate.update("""
                INSERT INTO public_charging_stations (
                    location, state, district, city_village, cpo_name, import_batch_id
                ) VALUES (
                    'Admin dashboard reference fixture', 'Maharashtra',
                    'Pune', 'Pune', ?, ?
                )
                """, publicFixtureOperator, publicFixtureBatchId);
    }

    @AfterEach
    void cleanUp() {
        if (publicFixtureBatchId != null) {
            jdbcTemplate.update(
                    "DELETE FROM public_charging_stations WHERE import_batch_id = ?",
                    publicFixtureBatchId);
        }
        if (station != null) {
            jdbcTemplate.update("DELETE FROM bookings WHERE station_id = ?", station.getId());
            stationRepository.deleteById(station.getId());
        }
        if (normalUser != null) {
            deleteUserData(normalUser.getId());
            userRepository.deleteById(normalUser.getId());
        }
        if (admin != null) {
            deleteUserData(admin.getId());
            userRepository.deleteById(admin.getId());
        }
    }

    @Test
    void dashboardDataUsesRealCountsAndOnlyListsNormalUsers() throws Exception {
        String userToken = jwtService.generateToken(normalUser);
        String adminToken = jwtService.generateToken(admin);

        mockMvc.perform(get("/api/admin/stations/summary"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/stations/summary")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        long publicCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public_charging_stations", Long.class);
        long managedCount = stationRepository.count();
        mockMvc.perform(get("/api/admin/stations/summary")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicStations").value(publicCount))
                .andExpect(jsonPath("$.managedStations").value(managedCount))
                .andExpect(jsonPath("$.totalStations").value(publicCount + managedCount));

        mockMvc.perform(get("/api/stations/discovery")
                        .param("source", "PUBLIC")
                        .param("q", publicFixtureOperator)
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].source").value("PUBLIC_DIRECTORY"))
                .andExpect(jsonPath("$.content[0].bookingEnabled").value(true))
                .andExpect(jsonPath("$.content[0].bookable").value(true));

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        String users = mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].role", everyItem(is("USER"))))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertTrue(users.contains(normalUser.getEmail()));
        assertFalse(users.contains(admin.getEmail()));
        assertFalse(users.contains("\"password\""));

        mockMvc.perform(get("/api/stations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize((int) stationRepository.count())));

        mockMvc.perform(get("/api/bookings/admin/all")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize((int) bookingRepository.count())));
    }

    private User createUser(String email, UserRole role) {
        User user = new User();
        user.setName("Dashboard integration user");
        user.setEmail(email);
        user.setPassword("integration-test-password");
        user.setRole(role);
        return user;
    }

    private void deleteUserData(Long userId) {
        jdbcTemplate.update("DELETE FROM notifications WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM bookings WHERE user_id = ?", userId);
    }
}
