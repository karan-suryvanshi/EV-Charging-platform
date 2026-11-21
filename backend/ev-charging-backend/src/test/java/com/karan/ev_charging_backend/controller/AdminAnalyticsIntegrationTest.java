package com.karan.ev_charging_backend.controller;

import com.karan.ev_charging_backend.auth.JwtService;
import com.karan.ev_charging_backend.auth.User;
import com.karan.ev_charging_backend.auth.UserRepository;
import com.karan.ev_charging_backend.auth.UserRole;
import com.karan.ev_charging_backend.booking.Booking;
import com.karan.ev_charging_backend.booking.BookingRepository;
import com.karan.ev_charging_backend.booking.BookingStatus;
import com.karan.ev_charging_backend.publicstation.PublicChargingStationRepository;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@AutoConfigureMockMvc
class AdminAnalyticsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StationRepository stationRepository;

    @Autowired
    private PublicChargingStationRepository publicStationRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private User admin;
    private User normalUser;
    private Station managedStation;
    private String fixtureId;
    private String publicCity;

    @BeforeEach
    void setUp() {
        fixtureId = UUID.randomUUID().toString();
        admin = userRepository.save(createUser(
                "analytics-admin-" + fixtureId + "@example.test", UserRole.ADMIN));
        normalUser = userRepository.save(createUser(
                "analytics-user-" + fixtureId + "@example.test", UserRole.USER));

        managedStation = new Station();
        managedStation.setName("Analytics integration station");
        managedStation.setLocation("Analytics managed location " + fixtureId);
        managedStation.setChargerType("Integration");
        managedStation.setPricePerUnit(10.0);
        managedStation.setAvailable(true);
        managedStation = stationRepository.save(managedStation);

        fixtureId = "analytics-" + fixtureId;
        publicCity = "Analytics City " + fixtureId;
        jdbcTemplate.update("""
                INSERT INTO public_charging_stations (
                    cpo_name, state, district, city_village, location, import_batch_id
                ) VALUES
                    ('Analytics fixture one', 'Test State', 'Test District', ?, 'Test address one', ?),
                    ('Analytics fixture two', 'Test State', 'Test District', ?, 'Test address two', ?)
                """, publicCity, fixtureId, publicCity, fixtureId);

        saveBooking(BookingStatus.CONFIRMED, LocalTime.of(9, 0), LocalTime.of(10, 0));
        saveBooking(BookingStatus.CANCELLED, LocalTime.of(10, 0), LocalTime.of(11, 0));
    }

    @AfterEach
    void cleanUp() {
        if (managedStation != null && managedStation.getId() != null) {
            jdbcTemplate.update("DELETE FROM bookings WHERE station_id = ?", managedStation.getId());
            stationRepository.deleteById(managedStation.getId());
        }
        if (fixtureId != null) {
            jdbcTemplate.update(
                    "DELETE FROM public_charging_stations WHERE import_batch_id = ?",
                    fixtureId);
        }
        deleteUser(admin);
        deleteUser(normalUser);
    }

    @Test
    void analyticsAreAdminOnlyAndAggregatePersistedDataWithoutExposingUsers() throws Exception {
        String adminToken = jwtService.generateToken(admin);
        String userToken = jwtService.generateToken(normalUser);

        mockMvc.perform(get("/api/admin/analytics"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/analytics")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        String response = mockMvc.perform(get("/api/admin/analytics")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalStations").value(
                        publicStationRepository.count() + stationRepository.count()))
                .andExpect(jsonPath("$.publicStations").value(publicStationRepository.count()))
                .andExpect(jsonPath("$.managedStations").value(stationRepository.count()))
                .andExpect(jsonPath("$.totalUsers").value(userRepository.count()))
                .andExpect(jsonPath("$.normalUsers").value(1))
                .andExpect(jsonPath("$.adminCount").value(1))
                .andExpect(jsonPath("$.activeUsersAvailable").value(false))
                .andExpect(jsonPath("$.activeUsers").doesNotExist())
                .andExpect(jsonPath("$.totalBookings").value(2))
                .andExpect(jsonPath("$.confirmedBookings").value(1))
                .andExpect(jsonPath("$.cancelledBookings").value(1))
                .andExpect(jsonPath("$.revenueAvailable").value(false))
                .andExpect(jsonPath("$.revenue").doesNotExist())
                .andExpect(jsonPath("$.revenueUnavailableReason").value(
                        "Revenue data unavailable: no payment records are stored."))
                .andExpect(jsonPath("$.monthlyReports").isArray())
                .andExpect(jsonPath("$.monthlyReports").isNotEmpty())
                .andExpect(jsonPath("$.monthlyReports[11].month")
                        .value(YearMonth.now().toString()))
                .andExpect(jsonPath("$.monthlyReports[11].totalBookings").value(2))
                .andExpect(jsonPath("$.monthlyReports[11].confirmedBookings").value(1))
                .andExpect(jsonPath("$.monthlyReports[11].cancelledBookings").value(1))
                .andExpect(jsonPath("$.monthlyReports[11].revenueAvailable").value(false))
                .andExpect(jsonPath("$.monthlyReports[11].revenue").value(nullValue()))
                .andExpect(jsonPath(
                        "$.topPublicCities[?(@.place == '" + publicCity + "')].stationCount")
                        .value(hasItem(2)))
                .andExpect(jsonPath(
                        "$.topManagedLocations[?(@.place == '"
                                + managedStation.getLocation() + "')].stationCount")
                        .value(hasItem(1)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertFalse(response.contains(admin.getEmail()));
        assertFalse(response.contains(normalUser.getEmail()));
        assertFalse(response.toLowerCase().contains("password"));
    }

    private void saveBooking(BookingStatus status, LocalTime start, LocalTime end) {
        Booking booking = new Booking();
        booking.setStation(managedStation);
        booking.setUser(normalUser);
        booking.setCustomerName("Analytics test fixture");
        booking.setBookingDate(LocalDate.now().withDayOfMonth(1));
        booking.setStartTime(start);
        booking.setEndTime(end);
        booking.setStatus(status);
        bookingRepository.save(booking);
    }

    private User createUser(String email, UserRole role) {
        User user = new User();
        user.setName("Analytics test fixture");
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
