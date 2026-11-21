package com.karan.ev_charging_backend;

import com.karan.ev_charging_backend.publicstation.PublicChargingStation;
import com.karan.ev_charging_backend.publicstation.PublicChargingStationRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class EvChargingBackendApplicationTests {
	@Autowired
	private PublicChargingStationRepository publicStations;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private String fixtureBatchId;

	@BeforeEach
	void setUp() {
		fixtureBatchId = "test-" + UUID.randomUUID();
		String insert = """
				INSERT INTO public_charging_stations (
					source_record_id, source_page, cpo_name, ownership, state,
					district, city_village, location, latitude, longitude,
					charger_type, rating_kw, secondary_rating_kw, connector_count,
					import_batch_id, source_pdf_record_id, source_warnings
				) VALUES (?, 1, ?, 'Public', 'Maharashtra', 'Pune', 'Pune',
					?, 18.520400, 73.856700, ?, 50.00, '50 kW', 1, ?, NULL, NULL)
				""";

		for (int index = 0; index < 21; index++) {
			String chargerType = switch (index % 7) {
				case 0 -> "CCS-II";
				case 1 -> "CHAdeMO";
				case 2 -> "Type 2";
				case 3 -> "GB/T";
				case 4 -> "Bharat AC-001";
				case 5 -> "Bharat DC-001";
				default -> "Test Type 6";
			};
			jdbcTemplate.update(
					insert,
					900000 + index,
					"Test Operator " + (index % 2),
					"Pune test station " + index,
					chargerType,
					fixtureBatchId);
		}
	}

	@AfterEach
	void cleanUp() {
		if (fixtureBatchId != null) {
			jdbcTemplate.update(
					"DELETE FROM public_charging_stations WHERE import_batch_id = ?",
					fixtureBatchId);
		}
	}

	@Test
	void contextLoads() {
	}

	@Test
	void publicStationSearchUsesDatabasePaginationAndCombinedFilters() {
		PageRequest firstPage = PageRequest.of(0, 20, Sort.by("id").ascending());
		Page<PublicChargingStation> pune = publicStations.searchPublicStations(
				null, null, null, null, null, "Pune", firstPage);

		Assertions.assertTrue(pune.getTotalElements() > 0);
		Assertions.assertEquals(20, pune.getContent().size());
		Assertions.assertTrue(pune.getTotalPages() > 1);
		Assertions.assertTrue(pune.getContent().stream().anyMatch(station ->
				containsIgnoreCase(station.getCityVillage(), "Pune")
						|| containsIgnoreCase(station.getDistrict(), "Pune")
						|| containsIgnoreCase(station.getLocation(), "Pune")
						|| containsIgnoreCase(station.getCpoName(), "Pune")));
		Page<PublicChargingStation> secondPunePage = publicStations.searchPublicStations(
				null, null, null, null, null, "Pune",
				PageRequest.of(1, 20, Sort.by("id").ascending()));
		Assertions.assertNotEquals(
				pune.getContent().get(0).getId(),
				secondPunePage.getContent().get(0).getId());

		Page<PublicChargingStation> filtered = publicStations.searchPublicStations(
				"Maharashtra", "Pune", "Pune", null, null, null, firstPage);

		Assertions.assertTrue(filtered.getTotalElements() > 0);
		Assertions.assertTrue(filtered.getContent().stream().allMatch(station ->
				containsIgnoreCase(station.getState(), "Maharashtra")
						&& containsIgnoreCase(station.getDistrict(), "Pune")
						&& containsIgnoreCase(station.getCityVillage(), "Pune")));
	}

	@Test
	void publicStationApiSearchFiltersAndFilterOptionsArePublicAndPaginated() throws Exception {
		mockMvc.perform(get("/api/public-stations")
					.param("q", "Pune")
					.param("page", "0")
					.param("size", "20"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements", greaterThan(0)))
				.andExpect(jsonPath("$.totalPages", greaterThan(1)))
				.andExpect(jsonPath("$.content.length()").value(20));

		mockMvc.perform(get("/api/public-stations")
					.param("state", "Maharashtra")
					.param("district", "Pune")
					.param("city", "Pune")
					.param("chargerType", "CCS-II")
					.param("page", "0")
					.param("size", "20"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements", greaterThan(0)))
				.andExpect(jsonPath("$.content[0].state").value("Maharashtra"))
				.andExpect(jsonPath("$.content[0].district").value("Pune"))
				.andExpect(jsonPath("$.content[0].chargerType").value("CCS-II"));

		mockMvc.perform(get("/api/public-stations/filters")
					.param("state", "Maharashtra")
					.param("district", "Pune"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.states").isArray())
				.andExpect(jsonPath("$.districts").isArray())
				.andExpect(jsonPath("$.cities").isArray())
				.andExpect(jsonPath("$.chargerTypes").isArray())
				.andExpect(jsonPath("$.operators").isArray());

		mockMvc.perform(get("/api/public-stations")
					.param("page", "not-a-number")
					.param("size", "too-large-to-parse"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.number").value(0))
				.andExpect(jsonPath("$.size").value(20));
	}

	@Test
	void publicStationFilterOptionsComeFromTestFixtures() {
		List<String> states = publicStations.findDistinctStates();
		List<String> chargerTypes = publicStations.findDistinctChargerTypes();
		List<String> operators = publicStations.findDistinctOperators();

		Assertions.assertTrue(states.contains("Maharashtra"));
		Assertions.assertTrue(chargerTypes.size() > 6);
		Assertions.assertTrue(operators.size() > 1);
	}

	private boolean containsIgnoreCase(String value, String term) {
		return value != null && value.toLowerCase().contains(term.toLowerCase());
	}

}
