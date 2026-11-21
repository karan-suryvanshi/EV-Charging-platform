package com.karan.ev_charging_backend.booking;

import com.karan.ev_charging_backend.auth.JwtService;
import com.karan.ev_charging_backend.auth.User;
import com.karan.ev_charging_backend.auth.UserRepository;
import com.karan.ev_charging_backend.auth.UserRole;
import com.karan.ev_charging_backend.publicstation.PublicChargingStationRepository;
import com.karan.ev_charging_backend.station.Station;
import com.karan.ev_charging_backend.station.StationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureMockMvc
class BookingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private PublicChargingStationRepository publicStationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User testUser;
    private User testAdmin;
    private Station testStation;
    private String publicFixtureBatchId;
    private Long publicFixtureStationId;

    @BeforeEach
    void setup() {
        String fixture = UUID.randomUUID().toString();
        publicFixtureBatchId = "test-" + fixture;
        testUser = userRepository.save(createUser("integration-user-" + fixture + "@example.test", UserRole.USER));
        testAdmin = userRepository.save(createUser("integration-admin-" + fixture + "@example.test", UserRole.ADMIN));

        testStation = new Station();
        testStation.setName("Booking integration station " + fixture);
        testStation.setLocation("Pune, Maharashtra");
        testStation.setChargerType("Integration");
        testStation.setPricePerUnit(10.0);
        testStation.setAvailable(true);
        testStation.setLatitude(18.5204);
        testStation.setLongitude(73.8567);
        testStation = stationRepository.save(testStation);
        publicFixtureStationId = testStation.getId();

        jdbcTemplate.update("""
                INSERT INTO public_charging_stations (
                    id, location, state, district, city_village, import_batch_id
                ) VALUES (?, 'Test-only public reference',
                    'Maharashtra', 'Pune', 'Pune', ?)
                """, publicFixtureStationId, publicFixtureBatchId);
    }

    @AfterEach
    void cleanup() {
        if (testUser != null) {
            deleteUserData(testUser.getId());
        }
        if (testAdmin != null) {
            deleteUserData(testAdmin.getId());
        }
        if (testStation != null) {
            jdbcTemplate.update("DELETE FROM bookings WHERE station_id = ?", testStation.getId());
        }
        if (publicFixtureStationId != null) {
            jdbcTemplate.update("DELETE FROM bookings WHERE public_station_id = ?", publicFixtureStationId);
        }
        if (publicFixtureBatchId != null) {
            jdbcTemplate.update(
                    "DELETE FROM public_charging_stations WHERE import_batch_id = ?",
                    publicFixtureBatchId);
        }
        if (testStation != null) {
            stationRepository.deleteById(testStation.getId());
        }
        if (testUser != null) {
            userRepository.deleteById(testUser.getId());
        }
        if (testAdmin != null) {
            userRepository.deleteById(testAdmin.getId());
        }
    }

    @Test
    void concurrentRequestsForTheSameSlotAllowExactlyOneBooking() throws Exception {
        int attempts = 4;
        LocalDate date = LocalDate.now().plusDays(30);
        LocalTime start = LocalTime.of(14, 0);
        LocalTime end = LocalTime.of(15, 0);
        String token = jwtService.generateToken(testUser);
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch begin = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();

        try {
            for (int i = 0; i < attempts; i++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    if (!begin.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Concurrent booking start timed out.");
                    }
                    return postBooking(token, date, start, end);
                }));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS), "All concurrent requests should be ready.");
            begin.countDown();

            int successes = 0;
            int conflicts = 0;
            for (Future<Integer> result : results) {
                int status = result.get(15, TimeUnit.SECONDS);
                if (status == HttpStatus.OK.value()) {
                    successes++;
                } else if (status == HttpStatus.CONFLICT.value()) {
                    conflicts++;
                } else {
                    fail("Unexpected concurrent booking response: " + status);
                }
            }
            assertEquals(1, successes);
            assertEquals(attempts - 1, conflicts);
            assertEquals(1, bookingRepository.findAllByUser_Id(testUser.getId()).size());
        } finally {
            begin.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS), "Concurrent workers should terminate.");
        }
    }

    @Test
    void overlappingSlotsAreRejectedAndAdjacentSlotsAreAllowed() throws Exception {
        LocalDate date = LocalDate.now().plusDays(31);
        String token = jwtService.generateToken(testUser);
        assertEquals(200, postBooking(
                token, date, LocalTime.of(10, 0), LocalTime.of(11, 0)));

        assertEquals(409, postBooking(token, date, LocalTime.of(10, 0), LocalTime.of(11, 0)));
        assertEquals(409, postBooking(token, date, LocalTime.of(10, 30), LocalTime.of(11, 30)));
        assertEquals(409, postBooking(token, date, LocalTime.of(9, 30), LocalTime.of(10, 30)));
        assertEquals(409, postBooking(token, date, LocalTime.of(9, 0), LocalTime.of(12, 0)));
        assertEquals(200, postBooking(token, date, LocalTime.of(11, 0), LocalTime.of(12, 0)));
    }

    @Test
    void cancellationPersistsAndOnlyTheOwnerCanCancel() {
        Booking created = bookingService.createBooking(
                newBooking(LocalDate.now().plusDays(32), LocalTime.of(16, 0), LocalTime.of(17, 0)),
                testStation.getId(),
                testUser.getId());
        assertTrue(bookingRepository.findById(created.getId()).isPresent());

        Booking cancelled = bookingService.cancelBooking(created.getId(), testUser.getId());
        assertEquals(BookingStatus.CANCELLED, cancelled.getStatus());
        assertEquals(BookingStatus.CANCELLED, bookingRepository.findById(created.getId()).orElseThrow().getStatus());

        ResponseStatusException repeatedCancel = assertThrows(
                ResponseStatusException.class,
                () -> bookingService.cancelBooking(created.getId(), testUser.getId()));
        assertEquals(HttpStatus.CONFLICT, repeatedCancel.getStatusCode());

        ResponseStatusException otherUserCancel = assertThrows(
                ResponseStatusException.class,
                () -> bookingService.cancelBooking(created.getId(), testAdmin.getId()));
        assertEquals(HttpStatus.NOT_FOUND, otherUserCancel.getStatusCode());
    }

    @Test
    void bookingEndpointsEnforceAuthenticationRoleAndPublicStationEligibility() throws Exception {
        LocalDate date = LocalDate.now().plusDays(33);
        String payload = bookingJson(date);

        mockMvc.perform(post("/api/bookings/station/" + testStation.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());

        String userToken = jwtService.generateToken(testUser);
        String adminToken = jwtService.generateToken(testAdmin);

        mockMvc.perform(get("/api/bookings/admin/all")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/bookings/admin/all")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/bookings/station/" + testStation.getId())
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.station.id").value(testStation.getId()))
                .andExpect(jsonPath("$.user.password").doesNotExist());

        assertEquals(1, bookingRepository.findAllByUser_Id(testUser.getId()).size());

        assertEquals(testStation.getId(), publicFixtureStationId,
                "The integration fixture deliberately reuses a numeric ID across station sources.");

        mockMvc.perform(post("/api/bookings/public-station/" + publicFixtureStationId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.station").value(nullValue()))
                .andExpect(jsonPath("$.publicStation.id").value(publicFixtureStationId))
                .andExpect(jsonPath("$.status").value("REQUESTED"));
    }

    @Test
    void publicBookingRequestsAreSourceSafeAndAdminControlsAreIsolated() throws Exception {
        String userToken = jwtService.generateToken(testUser);
        String adminToken = jwtService.generateToken(testAdmin);
        LocalDate date = LocalDate.now().plusDays(34);
        String requestBody = bookingJson(date);

        assertTrue(publicStationRepository.findById(publicFixtureStationId).orElseThrow().isBookingEnabled());
        assertTrue(testStation.isBookingEnabled());

        mockMvc.perform(put("/api/admin/stations/PUBLIC_DIRECTORY/{id}/booking-enabled", publicFixtureStationId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingEnabled\":false}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/bookings/public-station/{id}", publicFixtureStationId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.publicStation.id").value(publicFixtureStationId))
                .andExpect(jsonPath("$.station").value(nullValue()));

        mockMvc.perform(post("/api/bookings/public-station/{id}", publicFixtureStationId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict());

        Booking publicRequest = bookingRepository.findAllByUser_Id(testUser.getId()).stream()
                .filter(booking -> booking.getPublicStation() != null)
                .findFirst()
                .orElseThrow();
        mockMvc.perform(put("/api/bookings/{id}/cancel", publicRequest.getId())
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(put("/api/admin/stations/PUBLIC_DIRECTORY/{id}/booking-enabled", publicFixtureStationId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingEnabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingEnabled").value(false));
        assertFalse(publicStationRepository.findById(publicFixtureStationId).orElseThrow().isBookingEnabled());
        assertTrue(stationRepository.findById(testStation.getId()).orElseThrow().isBookingEnabled());
        assertTrue(stationRepository.findById(testStation.getId()).orElseThrow().isAvailable());

        mockMvc.perform(post("/api/bookings/public-station/{id}", publicFixtureStationId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(LocalDate.now().plusDays(35))))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/admin/stations/PUBLIC_DIRECTORY/{id}/booking-enabled", publicFixtureStationId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingEnabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingEnabled").value(true));
        assertTrue(publicStationRepository.findById(publicFixtureStationId).orElseThrow().isBookingEnabled());

        mockMvc.perform(put("/api/admin/stations/MANAGED/{id}/booking-enabled", testStation.getId())
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingEnabled\":false}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/admin/stations/MANAGED/{id}/booking-enabled", testStation.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingEnabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingEnabled").value(false));
        assertTrue(publicStationRepository.findById(publicFixtureStationId).orElseThrow().isBookingEnabled());
        assertTrue(stationRepository.findById(testStation.getId()).orElseThrow().isAvailable());
        mockMvc.perform(post("/api/bookings/station/{id}", testStation.getId())
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookingJson(LocalDate.now().plusDays(37))))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/admin/stations/MANAGED/{id}/booking-enabled", testStation.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingEnabled\":true}"))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanApprovePublicRequestAndUserHistoryShowsConfirmedStatus() throws Exception {
        Booking request = bookingService.createPublicBooking(
                newBooking(LocalDate.now().plusDays(36), LocalTime.of(12, 0), LocalTime.of(13, 0)),
                publicFixtureStationId,
                testUser.getId());
        String adminToken = jwtService.generateToken(testAdmin);
        String userToken = jwtService.generateToken(testUser);

        mockMvc.perform(put("/api/bookings/admin/{id}/status", request.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CONFIRMED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.station").value(nullValue()))
                .andExpect(jsonPath("$.publicStation.id").value(publicFixtureStationId));

        mockMvc.perform(get("/api/bookings")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(request.getId()))
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"));

        assertEquals(BookingStatus.CONFIRMED,
                bookingRepository.findById(request.getId()).orElseThrow().getStatus());
    }

    @Test
    void adminCanCancelRequestedPublicBooking() throws Exception {
        Booking request = bookingService.createPublicBooking(
                newBooking(LocalDate.now().plusDays(37), LocalTime.of(12, 0), LocalTime.of(13, 0)),
                publicFixtureStationId,
                testUser.getId());

        mockMvc.perform(put("/api/bookings/admin/{id}/status", request.getId())
                        .header("Authorization", "Bearer " + jwtService.generateToken(testAdmin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/api/bookings")
                        .header("Authorization", "Bearer " + jwtService.generateToken(testUser)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(request.getId()))
                .andExpect(jsonPath("$[0].status").value("CANCELLED"));

        assertEquals(BookingStatus.CANCELLED,
                bookingRepository.findById(request.getId()).orElseThrow().getStatus());
    }

    @Test
    void adminStatusEndpointRequiresAdminAndRejectsEveryNonRequestedTransition() throws Exception {
        Booking requested = bookingService.createPublicBooking(
                newBooking(LocalDate.now().plusDays(38), LocalTime.of(12, 0), LocalTime.of(13, 0)),
                publicFixtureStationId,
                testUser.getId());
        String endpoint = "/api/bookings/admin/{id}/status";
        String confirmBody = "{\"status\":\"CONFIRMED\"}";
        String cancelBody = "{\"status\":\"CANCELLED\"}";
        String requestedBody = "{\"status\":\"REQUESTED\"}";
        String adminToken = jwtService.generateToken(testAdmin);
        String userToken = jwtService.generateToken(testUser);

        mockMvc.perform(put(endpoint, requested.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put(endpoint, requested.getId())
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody))
                .andExpect(status().isForbidden());

        mockMvc.perform(put(endpoint, requested.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestedBody))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put(endpoint, requested.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody))
                .andExpect(status().isOk());
        mockMvc.perform(put(endpoint, requested.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelBody))
                .andExpect(status().isConflict());
        mockMvc.perform(put(endpoint, requested.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestedBody))
                .andExpect(status().isBadRequest());

        Booking cancelled = bookingService.createPublicBooking(
                newBooking(LocalDate.now().plusDays(39), LocalTime.of(12, 0), LocalTime.of(13, 0)),
                publicFixtureStationId,
                testUser.getId());
        mockMvc.perform(put(endpoint, cancelled.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cancelBody))
                .andExpect(status().isOk());
        mockMvc.perform(put(endpoint, cancelled.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody))
                .andExpect(status().isConflict());
        mockMvc.perform(put(endpoint, cancelled.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestedBody))
                .andExpect(status().isBadRequest());
    }

    private User createUser(String email, UserRole role) {
        User user = new User();
        user.setName("Integration Test User");
        user.setEmail(email);
        user.setPassword("integration-test-password");
        user.setRole(role);
        return user;
    }

    private Booking newBooking(LocalDate date, LocalTime start, LocalTime end) {
        Booking booking = new Booking();
        booking.setBookingDate(date);
        booking.setStartTime(start);
        booking.setEndTime(end);
        return booking;
    }

    private int postBooking(
            String token,
            LocalDate date,
            LocalTime start,
            LocalTime end) throws Exception {
        String payload = """
                {"bookingDate":"%s","startTime":"%s","endTime":"%s"}
                """.formatted(date, start, end);
        return mockMvc.perform(post("/api/bookings/station/{stationId}", testStation.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private int postPublicBooking(
            String token,
            LocalDate date,
            LocalTime start,
            LocalTime end) throws Exception {
        String payload = """
                {"bookingDate":"%s","startTime":"%s","endTime":"%s"}
                """.formatted(date, start, end);
        return mockMvc.perform(post("/api/bookings/public-station/{stationId}", publicFixtureStationId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private String bookingJson(LocalDate date) {
        return "{\"bookingDate\":\"" + date
                + "\",\"startTime\":\"10:00:00\",\"endTime\":\"11:00:00\"}";
    }

    private void deleteUserData(Long userId) {
        jdbcTemplate.update("DELETE FROM notifications WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM bookings WHERE user_id = ?", userId);
    }
}
