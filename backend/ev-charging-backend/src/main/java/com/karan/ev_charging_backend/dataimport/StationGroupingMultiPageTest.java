
package com.karan.ev_charging_backend.dataimport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
 * Experimental multi-page EV charging station PDF parser.
 * Generates a validation report and CSV files.
 * Does not write to the database.
 */
public class StationGroupingMultiPageTest extends PDFTextStripper {

    private static final Pattern LAT_PATTERN =
            Pattern.compile("^\\d{1,2}\\.\\d{3,}$");
    private static final Pattern LON_PATTERN =
            Pattern.compile("^\\d{1,3}\\.\\d{3,}$");

    private final List<TextPosition> pageCharacters = new ArrayList<>();

    public StationGroupingMultiPageTest() throws IOException {
        super();
        setSortByPosition(true);
    }

    @Override
    protected void writeString(String text, List<TextPosition> positions) {
        pageCharacters.addAll(positions);
    }

    public static class TextRow {
        public final double y;
        public final List<TextPosition> positions;

        public TextRow(double y, List<TextPosition> positions) {
            this.y = y;
            this.positions = positions;
            this.positions.sort(
                    Comparator.comparingDouble(TextPosition::getXDirAdj));
        }

        public String getTextIn(double minX, double maxX) {
            StringBuilder sb = new StringBuilder();

            for (TextPosition p : positions) {
                float x = p.getXDirAdj();

                if (x >= minX && x < maxX) {
                    sb.append(p.getUnicode());
                }
            }

            return sb.toString().replaceAll("\\s+", " ").trim();
        }

        public String getFullRowText() {
            StringBuilder sb = new StringBuilder();

            for (TextPosition p : positions) {
                sb.append(p.getUnicode());
            }

            return sb.toString().replaceAll("\\s+", " ").trim();
        }
    }

    public static class CandidateStation {
        public int globalIndex;
        public int pageRecordIndex;
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

    public static class PageParseResult {
        public int pageNumber;
        public int totalCharacters;
        public int totalRows;

        public List<String> headerRows = new ArrayList<>();
        public List<String> orphanedRows = new ArrayList<>();
        public List<CandidateStation> stations = new ArrayList<>();
    }

    public static List<TextRow> clusterIntoRows(
            List<TextPosition> characters, double yTolerance) {

        characters.sort(
                Comparator.comparingDouble(TextPosition::getYDirAdj)
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

    /**
     * Prints raw row extraction for a single PDF page.
     */
    public static void inspectPage(Path pdfPath, int pageNumber)
            throws IOException {

        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            if (pageNumber < 1
                    || pageNumber > document.getNumberOfPages()) {
                throw new IllegalArgumentException(
                        "Page number must be between 1 and "
                                + document.getNumberOfPages());
            }

            StationGroupingMultiPageTest extractor =
                    new StationGroupingMultiPageTest();

            extractor.setStartPage(pageNumber);
            extractor.setEndPage(pageNumber);
            extractor.getText(document);

            List<TextRow> rows = clusterIntoRows(
                    new ArrayList<>(extractor.pageCharacters), 2.0);

            System.out.printf(
                    "===== RAW ROW INSPECTION: PDF PAGE %d =====%n",
                    pageNumber);

            for (TextRow row : rows) {
                if (row.y < 72.0) {
                    continue;
                }

                System.out.printf(
                        "Y=%7.2f | CPO=%-22s | GOVT/PRIVATE=%-14s"
                                + " | STATE=%-22s | DISTRICT=%-20s"
                                + " | CITY=%-18s | LOCATION=%s%n",
                        row.y,
                        row.getTextIn(45, 82),
                        row.getTextIn(82, 115),
                        row.getTextIn(115, 175),
                        row.getTextIn(175, 245),
                        row.getTextIn(245, 305),
                        row.getTextIn(305, 502));

                System.out.printf(
                        "          | LAT=%-13s | LON=%-13s"
                                + " | CHARGER=%-30s | RATING=%-8s"
                                + " | SECONDARY=%-8s | COUNT=%s%n"
                                + "          | FULL ROW: %s%n",
                        row.getTextIn(502, 538),
                        row.getTextIn(538, 570),
                        row.getTextIn(570, 655),
                        row.getTextIn(650, 675),
                        row.getTextIn(675, 710),
                        row.getTextIn(710, 750),
                        row.getFullRowText());
            }

            System.out.println("===== END RAW ROW INSPECTION =====");
        }
    }

    public static PageParseResult parsePage(
            List<TextPosition> characters,
            int pageNumber,
            int globalStartIndex) {

        PageParseResult result = new PageParseResult();
        result.pageNumber = pageNumber;
        result.totalCharacters = characters.size();

        List<TextRow> allRows = clusterIntoRows(
                new ArrayList<>(characters), 2.0);

        result.totalRows = allRows.size();

        List<TextRow> dataRows = new ArrayList<>();

        for (TextRow row : allRows) {
            if (row.y < 72.0) {
                result.headerRows.add(String.format(
                        "Y=%.2f: %s",
                        row.y, row.getFullRowText()));
            } else {
                dataRows.add(row);
            }
        }

        List<TextRow> anchorRows = new ArrayList<>();

        for (TextRow row : dataRows) {
            String latText = row.getTextIn(502, 538);
            String lonText = row.getTextIn(538, 570);

            if (LAT_PATTERN.matcher(latText).matches()
                    && LON_PATTERN.matcher(lonText).matches()) {
                anchorRows.add(row);
            }
        }

        if (anchorRows.isEmpty()) {
            for (TextRow row : dataRows) {
                result.orphanedRows.add(String.format(
                        "No coordinate anchor found on page %d; "
                                + "row at Y=%.2f: \"%s\"",
                        pageNumber, row.y, row.getFullRowText()));
            }

            return result;
        }

        for (int i = 0; i < anchorRows.size(); i++) {
            TextRow anchor = anchorRows.get(i);

            CandidateStation station = new CandidateStation();
            station.globalIndex = globalStartIndex + i;
            station.pageRecordIndex = i + 1;
            station.pageNumber = pageNumber;
            station.anchorY = anchor.y;

            result.stations.add(station);
        }

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

            if (bestAnchorIdx == -1) {
                result.orphanedRows.add(String.format(
                        "Could not find an anchor for row at Y=%.2f: \"%s\"",
                        row.y, row.getFullRowText()));
                continue;
            }

            if (bestDist > 12.0) {
                result.orphanedRows.add(String.format(
                        "Orphaned row at Y=%.2f "
                                + "(nearest anchor Y=%.2f, dist=%.2f): \"%s\"",
                        row.y,
                        anchorRows.get(bestAnchorIdx).y,
                        bestDist,
                        row.getFullRowText()));
                continue;
            }

            CandidateStation station =
                    result.stations.get(bestAnchorIdx);

            if (bestAnchorIdx > 0) {
                double distPrev = Math.abs(
                        row.y - anchorRows.get(bestAnchorIdx - 1).y);

                if (Math.abs(distPrev - bestDist) < 1.0) {
                    station.warnings.add(String.format(
                            "Row at Y=%.2f is ambiguous between "
                                    + "Anchor #%d (Y=%.2f) and "
                                    + "Anchor #%d (Y=%.2f)",
                            row.y,
                            bestAnchorIdx,
                            anchorRows.get(bestAnchorIdx - 1).y,
                            bestAnchorIdx + 1,
                            station.anchorY));
                }
            }

            if (bestAnchorIdx < anchorRows.size() - 1) {
                double distNext = Math.abs(
                        row.y - anchorRows.get(bestAnchorIdx + 1).y);

                if (Math.abs(distNext - bestDist) < 1.0) {
                    station.warnings.add(String.format(
                            "Row at Y=%.2f is ambiguous between "
                                    + "Anchor #%d (Y=%.2f) and "
                                    + "Anchor #%d (Y=%.2f)",
                            row.y,
                            bestAnchorIdx + 1,
                            station.anchorY,
                            bestAnchorIdx + 2,
                            anchorRows.get(bestAnchorIdx + 1).y));
                }
            }

            station.rows.add(row);
        }

        Set<TextRow> globallyAssignedRows =
                Collections.newSetFromMap(new IdentityHashMap<>());

        for (CandidateStation station : result.stations) {
            station.rows.sort(Comparator.comparingDouble(r -> r.y));
            station.rowCount = station.rows.size();

            if (!station.rows.isEmpty()) {
                station.minY = station.rows.get(0).y;
                station.maxY =
                        station.rows.get(station.rows.size() - 1).y;
            } else {
                station.minY = station.anchorY;
                station.maxY = station.anchorY;
            }

            for (TextRow row : station.rows) {
                if (!globallyAssignedRows.add(row)) {
                    station.warnings.add(String.format(
                            "Defensive diagnostic: duplicate row "
                                    + "assignment at Y=%.2f",
                            row.y));
                }
            }
        }

        for (int i = 0; i < result.stations.size(); i++) {
            CandidateStation station = result.stations.get(i);

            double span = station.maxY - station.minY;

            if (span > 18.0) {
                station.warnings.add(String.format(
                        "Suspicious vertical span of %.2f pt across %d rows",
                        span, station.rowCount));
            }

            if (i < result.stations.size() - 1) {
                CandidateStation next = result.stations.get(i + 1);

                double anchorGap = next.anchorY - station.anchorY;
                double clearance = next.minY - station.maxY;

                if (anchorGap < 5.0) {
                    station.warnings.add(String.format(
                            "Critical anchor collision: next anchor "
                                    + "is only %.2f pt away",
                            anchorGap));
                }

                if (clearance < 2.0) {
                    station.warnings.add(String.format(
                            "Overlapping station boundaries: Record #%d "
                                    + "bottom Y=%.2f, Record #%d top Y=%.2f, "
                                    + "clearance=%.2f pt",
                            station.globalIndex,
                            station.maxY,
                            next.globalIndex,
                            next.minY,
                            clearance));
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

            for (TextRow row : station.rows) {
                addIfNotEmpty(cpoParts, row.getTextIn(45, 82));
                addIfNotEmpty(govtPrivParts, row.getTextIn(82, 115));
                addIfNotEmpty(stateParts, row.getTextIn(115, 175));
                addIfNotEmpty(districtParts, row.getTextIn(175, 245));
                addIfNotEmpty(cityParts, row.getTextIn(245, 305));
                addIfNotEmpty(locationParts, row.getTextIn(305, 502));

                String lat = row.getTextIn(502, 538);

                if (LAT_PATTERN.matcher(lat).matches()) {
                    if (station.latitude.isEmpty()) {
                        station.latitude = lat;
                    } else if (!station.latitude.equals(lat)) {
                        station.warnings.add(String.format(
                                "Conflicting latitudes: %s vs %s",
                                station.latitude, lat));
                    }
                }

                String lon = row.getTextIn(538, 570);

                if (LON_PATTERN.matcher(lon).matches()) {
                    if (station.longitude.isEmpty()) {
                        station.longitude = lon;
                    } else if (!station.longitude.equals(lon)) {
                        station.warnings.add(String.format(
                                "Conflicting longitudes: %s vs %s",
                                station.longitude, lon));
                    }
                }

                addIfNotEmpty(chargerParts, row.getTextIn(570, 655));
                addIfNotEmpty(ratingParts, row.getTextIn(650, 675));
                addIfNotEmpty(secRatingParts, row.getTextIn(675, 710));
                addIfNotEmpty(countParts, row.getTextIn(710, 750));
            }

            station.cpoName = joinParts(cpoParts);
            station.govtPrivate = joinParts(govtPrivParts);
            station.state = joinParts(stateParts);
            station.district = joinParts(districtParts);
            station.cityVillage = joinParts(cityParts);
            station.location = joinParts(locationParts);
            station.chargerType = joinParts(chargerParts);
            station.rating = joinParts(ratingParts);
            station.secondaryRating = joinParts(secRatingParts);
            station.connectorCount = joinParts(countParts);

            if (station.latitude.isEmpty()
                    || station.longitude.isEmpty()) {
                station.warnings.add("Missing Latitude or Longitude");
            }

            if (station.cpoName.isEmpty()) {
                station.warnings.add("Missing CPO Name");
            }

            if (station.state.isEmpty()) {
                station.warnings.add("Missing State");
            }

            if (station.location.isEmpty()) {
                station.warnings.add("Missing Location");
            }

            if (i > 0) {
                CandidateStation previous =
                        result.stations.get(i - 1);

                if (!station.latitude.isEmpty()
                        && station.latitude.equals(previous.latitude)
                        && !station.longitude.isEmpty()
                        && station.longitude.equals(previous.longitude)) {
                    station.warnings.add(String.format(
                            "Identical coordinates as Record #%d "
                                    + "(Lat: %s, Lon: %s)",
                            previous.globalIndex,
                            station.latitude,
                            station.longitude));
                }
            }
        }

        return result;
    }

    private static void addIfNotEmpty(
            List<String> parts, String value) {

        if (value != null && !value.isBlank()) {
            parts.add(value.trim());
        }
    }

    private static String joinParts(List<String> parts) {
        return String.join(" ", parts)
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Escapes a value for a CSV field.
     */
    private static String csvEscape(String value) {
        if (value == null) {
            return "";
        }

        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    /**
     * Exports candidate records to CSV.
     *
     * includeWarnings=false -> records without warnings
     * includeWarnings=true  -> records with warnings
     */
    private static void exportCsv(
            List<CandidateStation> stations,
            Path outputPath,
            boolean includeWarnings) throws IOException {

        StringBuilder csv = new StringBuilder();

        csv.append(
                "record_id,page_number,cpo_name,ownership,state,district,"
                        + "city_village,location,latitude,longitude,"
                        + "charger_type,rating_kw,secondary_rating_kw,"
                        + "connector_count,warnings\n");

        int exported = 0;

        for (CandidateStation station : stations) {
            if (station.hasWarnings() != includeWarnings) {
                continue;
            }

            csv.append(station.globalIndex).append(',')
                    .append(station.pageNumber).append(',')
                    .append(csvEscape(station.cpoName)).append(',')
                    .append(csvEscape(station.govtPrivate)).append(',')
                    .append(csvEscape(station.state)).append(',')
                    .append(csvEscape(station.district)).append(',')
                    .append(csvEscape(station.cityVillage)).append(',')
                    .append(csvEscape(station.location)).append(',')
                    .append(csvEscape(station.latitude)).append(',')
                    .append(csvEscape(station.longitude)).append(',')
                    .append(csvEscape(station.chargerType)).append(',')
                    .append(csvEscape(station.rating)).append(',')
                    .append(csvEscape(station.secondaryRating)).append(',')
                    .append(csvEscape(station.connectorCount)).append(',')
                    .append(csvEscape(String.join(" | ", station.warnings)))
                    .append('\n');

            exported++;
        }

        Files.writeString(
                outputPath, csv.toString(), StandardCharsets.UTF_8);

        System.out.printf(
                "CSV exported: %s (%d records)%n",
                outputPath, exported);
    }

    public static void main(String[] args) throws IOException {
        Path defaultDirectory = Path.of("C:/Users/Asus-pc/Downloads");
        boolean inspect = args.length >= 2 && "inspect".equalsIgnoreCase(args[0]);
        Path pdfPath = inspect || args.length == 0
                ? defaultDirectory.resolve("Ev_charging_station_data.pdf")
                : Path.of(args[0]);

        if (inspect) {
            inspectPage(pdfPath, Integer.parseInt(args[1]));
            return;
        }

        Path outputDirectory = args.length >= 2
                ? Path.of(args[1])
                : defaultDirectory;
        Files.createDirectories(outputDirectory);
        Path outputPath = outputDirectory.resolve(
                "station_grouping_full_validation.txt");
        Path cleanCsvPath = outputDirectory.resolve("stations_clean.csv");
        Path reviewCsvPath = outputDirectory.resolve("stations_review.csv");
        int startPage = args.length >= 3 ? Integer.parseInt(args[2]) : 1;
        int requestedEndPage = args.length >= 4
                ? Integer.parseInt(args[3])
                : Integer.MAX_VALUE;

        List<PageParseResult> pageResults = new ArrayList<>();
        List<CandidateStation> allStations = new ArrayList<>();

        int globalStationCounter = 1;

        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            int totalPdfPages = document.getNumberOfPages();
            int endPage = Math.min(requestedEndPage, totalPdfPages);
            if (startPage < 1 || startPage > endPage) {
                throw new IllegalArgumentException(
                        "Invalid page range " + startPage + " to " + endPage);
            }

            System.out.printf(
                    "Processing pages %d to %d from %s...%n",
                    startPage, endPage, pdfPath);

            System.out.printf(
                    "Total pages in PDF document: %d%n",
                    totalPdfPages);

            if (endPage > totalPdfPages) {
                throw new IllegalArgumentException(
                        "Configured end page " + endPage
                                + " exceeds PDF page count "
                                + totalPdfPages);
            }

            for (int page = startPage; page <= endPage; page++) {
                StationGroupingMultiPageTest extractor =
                        new StationGroupingMultiPageTest();

                extractor.setStartPage(page);
                extractor.setEndPage(page);
                extractor.getText(document);

                PageParseResult pageResult = parsePage(
                        extractor.pageCharacters,
                        page,
                        globalStationCounter);

                pageResults.add(pageResult);
                allStations.addAll(pageResult.stations);

                globalStationCounter += pageResult.stations.size();

                System.out.printf(
                        "Page %d: %d characters, %d rows, "
                                + "%d candidate stations%n",
                        page,
                        pageResult.totalCharacters,
                        pageResult.totalRows,
                        pageResult.stations.size());
            }

            int recordsWithWarnings = 0;
            int singleLineCount = 0;
            int twoLineCount = 0;
            int threeLineCount = 0;
            int otherLineCount = 0;
            int totalWarnings = 0;

            StringBuilder report = new StringBuilder();

            report.append(
                    "================================================================================\n");
            report.append(
                    " EV CHARGING STATION GROUPING PROTOTYPE - FULL VALIDATION REPORT\n");
            report.append(
                    "================================================================================\n");

            report.append(String.format(
                    "Source PDF          : %s%n", pdfPath));
            report.append(String.format(
                    "Pages Evaluated     : %d to %d%n",
                    startPage, endPage));
            report.append(String.format(
                    "Candidate Records   : %d%n", allStations.size()));
            report.append(String.format(
                    "Report Destination  : %s%n", outputPath));

            report.append(
                    "NOTE: Candidate records are not yet approved for database import.\n");
            report.append(
                    "================================================================================\n\n");

            for (PageParseResult pageResult : pageResults) {
                report.append(
                        "********************************************************************************\n");

                report.append(String.format(
                        ">>> PAGE %d (Characters: %d, Rows: %d, Candidate Records: %d)%n",
                        pageResult.pageNumber,
                        pageResult.totalCharacters,
                        pageResult.totalRows,
                        pageResult.stations.size()));

                report.append(
                        "********************************************************************************\n");

                if (!pageResult.headerRows.isEmpty()) {
                    report.append("HEADER ROWS:\n");

                    for (String header : pageResult.headerRows) {
                        report.append("  ")
                                .append(header)
                                .append('\n');
                    }

                    report.append('\n');
                }

                if (!pageResult.orphanedRows.isEmpty()) {
                    report.append("ORPHANED / UNASSIGNED ROWS:\n");

                    for (String orphan : pageResult.orphanedRows) {
                        report.append("  [!] ")
                                .append(orphan)
                                .append('\n');
                    }

                    report.append('\n');
                }

                for (CandidateStation station : pageResult.stations) {
                    if (station.hasWarnings()) {
                        recordsWithWarnings++;
                        totalWarnings += station.warnings.size();
                    }

                    if (station.rowCount == 1) {
                        singleLineCount++;
                    } else if (station.rowCount == 2) {
                        twoLineCount++;
                    } else if (station.rowCount == 3) {
                        threeLineCount++;
                    } else {
                        otherLineCount++;
                    }

                    report.append(
                            "--------------------------------------------------------------------------------\n");

                    report.append(String.format(
                            "RECORD #%-5d (Page %d, Page Record #%-3d) "
                                    + "| Anchor Y: %7.2f | Rows: %d "
                                    + "(Y: %.2f to %.2f)%n",
                            station.globalIndex,
                            station.pageNumber,
                            station.pageRecordIndex,
                            station.anchorY,
                            station.rowCount,
                            station.minY,
                            station.maxY));

                    report.append(
                            "--------------------------------------------------------------------------------\n");

                    report.append(String.format(
                            "CPO Name        : %s%n", station.cpoName));
                    report.append(String.format(
                            "Govt / Private  : %s%n", station.govtPrivate));
                    report.append(String.format(
                            "State           : %s%n", station.state));
                    report.append(String.format(
                            "District        : %s%n", station.district));
                    report.append(String.format(
                            "City / Village  : %s%n", station.cityVillage));
                    report.append(String.format(
                            "Location        : %s%n", station.location));
                    report.append(String.format(
                            "Latitude        : %s%n", station.latitude));
                    report.append(String.format(
                            "Longitude       : %s%n", station.longitude));
                    report.append(String.format(
                            "Charger Type    : %s%n", station.chargerType));
                    report.append(String.format(
                            "Rating (kW)     : %s%n", station.rating));

                    if (!station.secondaryRating.isEmpty()
                            && !station.secondaryRating.equals(station.rating)) {
                        report.append(String.format(
                                "Sec. Rating(kW) : %s%n",
                                station.secondaryRating));
                    }

                    report.append(String.format(
                            "Connector Count : %s%n",
                            station.connectorCount));

                    if (station.hasWarnings()) {
                        report.append(String.format(
                                "WARNINGS (%d)   :%n",
                                station.warnings.size()));

                        for (String warning : station.warnings) {
                            report.append("  [!] ")
                                    .append(warning)
                                    .append('\n');
                        }
                    } else {
                        report.append(
                                "WARNINGS        : None (Clean Record)\n");
                    }

                    report.append('\n');
                }
            }

            report.append(
                    "================================================================================\n");
            report.append("OVERALL SUMMARY\n");
            report.append(
                    "================================================================================\n");

            report.append(String.format(
                    "Total Candidate Records : %d%n", allStations.size()));
            report.append(String.format(
                    "  - 1-row records       : %d%n", singleLineCount));
            report.append(String.format(
                    "  - 2-row records       : %d%n", twoLineCount));
            report.append(String.format(
                    "  - 3-row records       : %d%n", threeLineCount));
            report.append(String.format(
                    "  - >3-row records      : %d%n", otherLineCount));
            report.append(String.format(
                    "Records with Warnings   : %d%n", recordsWithWarnings));
            report.append(String.format(
                    "Total Warning Messages  : %d%n", totalWarnings));

            report.append(
                    "WARNING: This report is for validation only; do not import candidates blindly.\n");
            report.append(
                    "================================================================================\n");

            Files.writeString(
                    outputPath,
                    report.toString(),
                    StandardCharsets.UTF_8);

            System.out.printf(
                    "Validation report saved: %s%n", outputPath);

            // Export candidate data separately. This does not touch MySQL.
            exportCsv(allStations, cleanCsvPath, false);
            exportCsv(allStations, reviewCsvPath, true);

            System.out.println("\n===== FULL VALIDATION SUMMARY =====");
            System.out.printf(
                    "Pages processed: %d-%d%n", startPage, endPage);
            System.out.printf(
                    "Candidate records: %d%n", allStations.size());

            System.out.printf(
                    "Row layout: 1=%d, 2=%d, 3=%d, >3=%d%n",
                    singleLineCount,
                    twoLineCount,
                    threeLineCount,
                    otherLineCount);

            System.out.printf(
                    "Records with warnings: %d%n", recordsWithWarnings);
            System.out.printf(
                    "Total warning messages: %d%n", totalWarnings);

            System.out.println("===================================");
        }
    }
}
