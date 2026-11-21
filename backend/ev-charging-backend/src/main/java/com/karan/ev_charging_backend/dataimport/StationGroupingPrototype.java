package com.karan.ev_charging_backend.dataimport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
 * Experimental prototype for grouping EV charging station records from PDF.
 * Uses Latitude and Longitude as record anchors and aggregates multi-line text
 * dynamically without hardcoding a 3-line assumption.
 */
public class StationGroupingPrototype extends PDFTextStripper {

    private static final Pattern LAT_PATTERN = Pattern.compile("^\\d{1,2}\\.\\d{3,}$");
    private static final Pattern LON_PATTERN = Pattern.compile("^\\d{1,3}\\.\\d{3,}$");

    private final List<TextPosition> pageCharacters = new ArrayList<>();

    public StationGroupingPrototype() throws IOException {
        super();
        setSortByPosition(true);
    }

    @Override
    protected void writeString(String text, List<TextPosition> positions) {
        pageCharacters.addAll(positions);
    }

    public static class TextRow {
        final double y;
        final List<TextPosition> positions;

        TextRow(double y, List<TextPosition> positions) {
            this.y = y;
            this.positions = positions;
            this.positions.sort(Comparator.comparingDouble(TextPosition::getXDirAdj));
        }

        public String getTextIn(double minX, double maxX) {
            StringBuilder sb = new StringBuilder();
            for (TextPosition p : positions) {
                float x = p.getXDirAdj();
                if (x >= minX && x < maxX) {
                    sb.append(p.getUnicode());
                }
            }
            return sb.toString().trim();
        }

        public String getFullRowText() {
            StringBuilder sb = new StringBuilder();
            for (TextPosition p : positions) {
                sb.append(p.getUnicode());
            }
            return sb.toString().trim();
        }
    }

    public static class CandidateStation {
        public int recordIndex;
        public int pageNumber;
        public double anchorY;
        public double minY;
        public double maxY;
        public int rowCount;
        public List<TextRow> rows = new ArrayList<>();

        public String cpoName = "";
        public String govtPrivate = "";
        public String state = "";
        public String district = "";
        public String cityVillage = "";
        public String location = "";
        public String latitude = "";
        public String longitude = "";
        public String chargerType = "";
        public String rating = "";
        public String secondaryRating = "";
        public String connectorCount = "";

        public List<String> warnings = new ArrayList<>();

        public boolean hasWarnings() {
            return !warnings.isEmpty();
        }
    }

    public static List<TextRow> clusterIntoRows(List<TextPosition> characters, double yTolerance) {
        characters.sort(Comparator.comparingDouble(TextPosition::getYDirAdj)
                .thenComparingDouble(TextPosition::getXDirAdj));

        List<List<TextPosition>> rowGroups = new ArrayList<>();

        for (TextPosition p : characters) {
            double y = p.getYDirAdj();
            List<TextPosition> matchingRow = null;

            for (List<TextPosition> group : rowGroups) {
                double avgY = group.stream()
                        .mapToDouble(TextPosition::getYDirAdj)
                        .average()
                        .orElse(y);

                if (Math.abs(avgY - y) <= yTolerance) {
                    matchingRow = group;
                    break;
                }
            }

            if (matchingRow == null) {
                matchingRow = new ArrayList<>();
                rowGroups.add(matchingRow);
            }
            matchingRow.add(p);
        }

        List<TextRow> textRows = new ArrayList<>();
        for (List<TextPosition> group : rowGroups) {
            double avgY = group.stream()
                    .mapToDouble(TextPosition::getYDirAdj)
                    .average()
                    .orElse(0.0);
            textRows.add(new TextRow(avgY, group));
        }

        textRows.sort(Comparator.comparingDouble(r -> r.y));
        return textRows;
    }

    public static List<CandidateStation> parsePage(List<TextPosition> characters, int pageNumber) {
        // Step 1: Cluster characters into horizontal text rows
        List<TextRow> allRows = clusterIntoRows(characters, 2.0);

        // Step 2: Separate headers (Y < 72.0) from data rows
        List<TextRow> dataRows = new ArrayList<>();
        for (TextRow row : allRows) {
            if (row.y >= 72.0) {
                dataRows.add(row);
            }
        }

        // Step 3: Identify Candidate Anchor Rows using Latitude and Longitude columns
        // Latitude column: [500, 538), Longitude column: [538, 570)
        List<TextRow> anchorRows = new ArrayList<>();
        for (TextRow row : dataRows) {
            String latText = row.getTextIn(500, 538);
            String lonText = row.getTextIn(538, 570);

            if (LAT_PATTERN.matcher(latText).matches() && LON_PATTERN.matcher(lonText).matches()) {
                anchorRows.add(row);
            }
        }

        List<CandidateStation> candidates = new ArrayList<>();

        // If no anchors found on this page
        if (anchorRows.isEmpty()) {
            return candidates;
        }

        // Step 4: Map each data row to its appropriate anchor
        for (int i = 0; i < anchorRows.size(); i++) {
            TextRow anchor = anchorRows.get(i);
            CandidateStation station = new CandidateStation();
            station.recordIndex = i + 1;
            station.pageNumber = pageNumber;
            station.anchorY = anchor.y;

            candidates.add(station);
        }

        // Assign each data row to the nearest anchor
        for (TextRow row : dataRows) {
            int bestAnchorIdx = -1;
            double bestDist = Double.MAX_VALUE;

            for (int i = 0; i < anchorRows.size(); i++) {
                double dist = Math.abs(row.y - anchorRows.get(i).y);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestAnchorIdx = i;
                }
            }

            if (bestAnchorIdx != -1) {
                CandidateStation station = candidates.get(bestAnchorIdx);

                // Check for orphan / distant rows
                if (bestDist > 12.0) {
                    station.warnings.add(String.format(
                            "Row at Y=%.2f is far from anchor Y=%.2f (dist=%.2f): \"%s\"",
                            row.y, station.anchorY, bestDist, row.getFullRowText()
                    ));
                }

                // Check for ambiguity with adjacent anchors
                if (bestAnchorIdx > 0) {
                    double distPrev = Math.abs(row.y - anchorRows.get(bestAnchorIdx - 1).y);
                    if (Math.abs(distPrev - bestDist) < 1.0) {
                        station.warnings.add(String.format(
                                "Row at Y=%.2f ambiguous between Anchor %d (Y=%.2f) and Anchor %d (Y=%.2f)",
                                row.y, bestAnchorIdx, anchorRows.get(bestAnchorIdx - 1).y, bestAnchorIdx + 1, station.anchorY
                        ));
                    }
                }
                if (bestAnchorIdx < anchorRows.size() - 1) {
                    double distNext = Math.abs(row.y - anchorRows.get(bestAnchorIdx + 1).y);
                    if (Math.abs(distNext - bestDist) < 1.0) {
                        station.warnings.add(String.format(
                                "Row at Y=%.2f ambiguous between Anchor %d (Y=%.2f) and Anchor %d (Y=%.2f)",
                                row.y, bestAnchorIdx + 1, station.anchorY, bestAnchorIdx + 2, anchorRows.get(bestAnchorIdx + 1).y
                        ));
                    }
                }

                station.rows.add(row);
            }
        }

        // Step 5: Extract fields from collected candidate rows for each station
        for (int i = 0; i < candidates.size(); i++) {
            CandidateStation s = candidates.get(i);
            s.rows.sort(Comparator.comparingDouble(r -> r.y));
            s.rowCount = s.rows.size();

            if (!s.rows.isEmpty()) {
                s.minY = s.rows.get(0).y;
                s.maxY = s.rows.get(s.rows.size() - 1).y;
            } else {
                s.minY = s.anchorY;
                s.maxY = s.anchorY;
            }

            // Check boundary height
            double span = s.maxY - s.minY;
            if (span > 15.0) {
                s.warnings.add(String.format("Suspicious vertical span of %.2f pt across %d rows", span, s.rowCount));
            }

            // Check proximity to next anchor
            if (i < candidates.size() - 1) {
                double anchorGap = candidates.get(i + 1).anchorY - s.anchorY;
                if (anchorGap < 9.0) {
                    s.warnings.add(String.format("Very close to next anchor: gap = %.2f pt", anchorGap));
                }
            }

            List<String> cpoParts = new ArrayList<>();
            List<String> govtPrivParts = new ArrayList<>();
            List<String> stateParts = new ArrayList<>();
            List<String> districtParts = new ArrayList<>();
            List<String> cityParts = new ArrayList<>();
            List<String> locationParts = new ArrayList<>();
            List<String> chargerParts = new ArrayList<>();
            List<String> ratingParts = new ArrayList<>();
            List<String> secRatingParts = new ArrayList<>();
            List<String> countParts = new ArrayList<>();

            for (TextRow r : s.rows) {
                String cpo = r.getTextIn(45, 82);
                if (!cpo.isEmpty()) cpoParts.add(cpo);

                String gp = r.getTextIn(82, 115);
                if (!gp.isEmpty()) govtPrivParts.add(gp);

                String st = r.getTextIn(115, 175);
                if (!st.isEmpty()) stateParts.add(st);

                String dist = r.getTextIn(175, 245);
                if (!dist.isEmpty()) districtParts.add(dist);

                String city = r.getTextIn(245, 305);
                if (!city.isEmpty()) cityParts.add(city);

                String loc = r.getTextIn(305, 502);
                if (!loc.isEmpty()) locationParts.add(loc);

                String lat = r.getTextIn(500, 538);
                if (LAT_PATTERN.matcher(lat).matches()) {
                    if (s.latitude.isEmpty()) {
                        s.latitude = lat;
                    } else if (!s.latitude.equals(lat)) {
                        s.warnings.add(String.format("Conflicting latitudes in group: %s vs %s", s.latitude, lat));
                    }
                }

                String lon = r.getTextIn(538, 570);
                if (LON_PATTERN.matcher(lon).matches()) {
                    if (s.longitude.isEmpty()) {
                        s.longitude = lon;
                    } else if (!s.longitude.equals(lon)) {
                        s.warnings.add(String.format("Conflicting longitudes in group: %s vs %s", s.longitude, lon));
                    }
                }

                String ch = r.getTextIn(570, 650);
                if (!ch.isEmpty()) chargerParts.add(ch);

                String rat = r.getTextIn(650, 675);
                if (!rat.isEmpty()) ratingParts.add(rat);

                String secRat = r.getTextIn(675, 710);
                if (!secRat.isEmpty()) secRatingParts.add(secRat);

                String cnt = r.getTextIn(710, 750);
                if (!cnt.isEmpty()) countParts.add(cnt);
            }

            s.cpoName = String.join(" ", cpoParts).replaceAll("\\s+", " ").trim();
            s.govtPrivate = String.join(" ", govtPrivParts).replaceAll("\\s+", " ").trim();
            s.state = String.join(" ", stateParts).replaceAll("\\s+", " ").trim();
            s.district = String.join(" ", districtParts).replaceAll("\\s+", " ").trim();
            s.cityVillage = String.join(" ", cityParts).replaceAll("\\s+", " ").trim();
            s.location = String.join(" ", locationParts).replaceAll("\\s+", " ").trim();
            s.chargerType = String.join(" ", chargerParts).replaceAll("\\s+", " ").trim();
            s.rating = String.join(" ", ratingParts).replaceAll("\\s+", " ").trim();
            s.secondaryRating = String.join(" ", secRatingParts).replaceAll("\\s+", " ").trim();
            s.connectorCount = String.join(" ", countParts).replaceAll("\\s+", " ").trim();

            // Field completeness checks
            if (s.latitude.isEmpty() || s.longitude.isEmpty()) {
                s.warnings.add("Missing Latitude or Longitude");
            }
            if (s.cpoName.isEmpty()) {
                s.warnings.add("Missing CPO Name");
            }
            if (s.state.isEmpty()) {
                s.warnings.add("Missing State");
            }
            if (s.location.isEmpty()) {
                s.warnings.add("Missing Location");
            }

            // Duplicate coordinates check with previous record
            if (i > 0) {
                CandidateStation prev = candidates.get(i - 1);
                if (!s.latitude.isEmpty() && s.latitude.equals(prev.latitude)
                        && !s.longitude.isEmpty() && s.longitude.equals(prev.longitude)) {
                    s.warnings.add(String.format("Identical coordinates as Record #%d (Lat: %s, Lon: %s)",
                            prev.recordIndex, s.latitude, s.longitude));
                }
            }
        }

        return candidates;
    }

    public static void main(String[] args) throws IOException {
        Path pdfPath = Path.of("C:/Users/Asus-pc/Downloads/Ev_charging_station_data.pdf");
        Path outputPath = Path.of("C:/Users/Asus-pc/Downloads/station_grouping_preview.txt");

        System.out.println("Processing Page 1 of " + pdfPath + "...");

        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            StationGroupingPrototype extractor = new StationGroupingPrototype();
            extractor.setStartPage(1);
            extractor.setEndPage(1);
            extractor.getText(document);

            List<TextPosition> characters = extractor.pageCharacters;
            System.out.println("Page 1 character count: " + characters.size());

            List<CandidateStation> stations = parsePage(characters, 1);
            System.out.println("Total candidate stations identified on Page 1: " + stations.size());

            StringBuilder report = new StringBuilder();
            report.append("================================================================================\n");
            report.append("         EV CHARGING STATION GROUPING PROTOTYPE - PAGE 1 PREVIEW REPORT         \n");
            report.append("================================================================================\n");
            report.append(String.format("Total Stations Extracted: %d%n", stations.size()));
            report.append(String.format("Generated File: %s%n", outputPath));
            report.append("================================================================================\n\n");

            int warningCount = 0;

            for (CandidateStation s : stations) {
                if (s.hasWarnings()) {
                    warningCount++;
                }

                report.append("--------------------------------------------------------------------------------\n");
                report.append(String.format("RECORD #%-2d | Page: %d | Anchor Y: %6.2f | Rows: %d (Y: %.2f to %.2f)%n",
                        s.recordIndex, s.pageNumber, s.anchorY, s.rowCount, s.minY, s.maxY));
                report.append("--------------------------------------------------------------------------------\n");
                report.append(String.format("CPO Name        : %s%n", s.cpoName));
                report.append(String.format("Govt / Private  : %s%n", s.govtPrivate));
                report.append(String.format("State           : %s%n", s.state));
                report.append(String.format("District        : %s%n", s.district));
                report.append(String.format("City / Village  : %s%n", s.cityVillage));
                report.append(String.format("Location        : %s%n", s.location));
                report.append(String.format("Latitude        : %s%n", s.latitude));
                report.append(String.format("Longitude       : %s%n", s.longitude));
                report.append(String.format("Charger Type    : %s%n", s.chargerType));
                report.append(String.format("Rating (kW)     : %s%n", s.rating));
                if (!s.secondaryRating.isEmpty() && !s.secondaryRating.equals(s.rating)) {
                    report.append(String.format("Sec. Rating(kW) : %s%n", s.secondaryRating));
                }
                report.append(String.format("Connector Count : %s%n", s.connectorCount));

                if (s.hasWarnings()) {
                    report.append(String.format("WARNINGS (%d)   :%n", s.warnings.size()));
                    for (String w : s.warnings) {
                        report.append(String.format("  [!] %s%n", w));
                    }
                } else {
                    report.append("WARNINGS        : None (Clean Record)\n");
                }
                report.append("\n");
            }

            report.append("================================================================================\n");
            report.append(String.format("SUMMARY: %d Total Records, %d Clean, %d With Warnings%n",
                    stations.size(), (stations.size() - warningCount), warningCount));
            report.append("================================================================================\n");

            Files.writeString(outputPath, report.toString(), StandardCharsets.UTF_8);
            System.out.println("Preview successfully written to: " + outputPath);

            // Print summary and first 5 records to console
            System.out.println("\n===== FIRST 5 STATIONS EXTRACTED FROM PAGE 1 =====");
            for (int i = 0; i < Math.min(5, stations.size()); i++) {
                CandidateStation s = stations.get(i);
                System.out.println("--------------------------------------------------");
                System.out.printf("Station #%d (Anchor Y=%.2f, Rows=%d):%n", s.recordIndex, s.anchorY, s.rowCount);
                System.out.printf("  CPO Name   : %s%n", s.cpoName);
                System.out.printf("  Govt/Priv  : %s%n", s.govtPrivate);
                System.out.printf("  State      : %s%n", s.state);
                System.out.printf("  District   : %s%n", s.district);
                System.out.printf("  City       : %s%n", s.cityVillage);
                System.out.printf("  Location   : %s%n", s.location);
                System.out.printf("  Lat, Lon   : %s, %s%n", s.latitude, s.longitude);
                System.out.printf("  Charger    : %s%n", s.chargerType);
                System.out.printf("  Rating     : %s kW%n", s.rating);
                System.out.printf("  Count      : %s%n", s.connectorCount);
                System.out.printf("  Warnings   : %s%n", s.hasWarnings() ? s.warnings : "None");
            }
            System.out.println("==================================================");
        }
    }
}
