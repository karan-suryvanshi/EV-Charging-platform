package com.karan.ev_charging_backend.dataimport;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

public class StationRecordParser extends PDFTextStripper {

    private final List<TextPosition> characters = new ArrayList<>();

    public StationRecordParser() throws IOException {
        super();
    }

    @Override
    protected void writeString(String text, List<TextPosition> positions) {
        characters.addAll(positions);
    }

    public static void main(String[] args) throws IOException {

        Path pdfPath = Path.of(
            "C:/Users/Asus-pc/Downloads/Ev_charging_station_data.pdf"
        );

        Path outputPath = Path.of(
            "C:/Users/Asus-pc/Downloads/ev_station_position_preview.txt"
        );

        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {

            StationRecordParser parser = new StationRecordParser();
            parser.setSortByPosition(true);
            parser.setStartPage(1);
            parser.setEndPage(1);
            parser.getText(document);

            List<TextPosition> characters = parser.characters;

            System.out.println("\n===== COLUMN-WISE TEXT PREVIEW =====");

            for (TextPosition p : characters) {
                float x = p.getXDirAdj();

                if (x < 82) {
                    System.out.print(p.getUnicode() + " ");
                }
            }

            System.out.println("\n===== END PREVIEW =====");

            System.out.println("\n===== LATITUDE AND LONGITUDE ROWS =====");

            List<TextPosition> coordinateChars = characters.stream()
                .filter(p -> p.getXDirAdj() >= 500 && p.getXDirAdj() < 570)
                .toList();

            List<Double> yPositions = coordinateChars.stream()
                .map(p -> (double) p.getYDirAdj())
                .distinct()
                .sorted()
                .toList();

            for (double y : yPositions) {
                StringBuilder latitude = new StringBuilder();
                StringBuilder longitude = new StringBuilder();

                for (TextPosition p : coordinateChars) {
                    if (Math.abs(p.getYDirAdj() - y) > 0.5) {
                        continue;
                    }

                    float x = p.getXDirAdj();

                    if (x >= 500 && x < 538) {
                        latitude.append(p.getUnicode());
                    } else if (x >= 538 && x < 570) {
                        longitude.append(p.getUnicode());
                    }
                }

                if (!latitude.isEmpty() || !longitude.isEmpty()) {
                    System.out.printf(
                        "Y=%.2f | Latitude=%s | Longitude=%s%n",
                        y, latitude, longitude
                    );
                }
            }

            System.out.println("===== END COORDINATE ROWS =====");

            System.out.println("\n===== CPO NAME POSITIONS =====");

            for (TextPosition p : characters) {
                if (p.getXDirAdj() < 82
                        && p.getYDirAdj() >= 70
                        && p.getYDirAdj() <= 100) {
                    System.out.printf(
                        "X=%.2f | Y=%.2f | Text=%s%n",
                        p.getXDirAdj(),
                        p.getYDirAdj(),
                        p.getUnicode()
                    );
                }
            }

            System.out.println("===== END CPO NAME POSITIONS =====");

            System.out.println("\n===== LOCATION POSITIONS =====");

            for (TextPosition p : characters) {
                if (p.getXDirAdj() >= 390
                        && p.getXDirAdj() < 500
                        && p.getYDirAdj() >= 70
                        && p.getYDirAdj() <= 100) {
                    System.out.printf(
                        "X=%.2f | Y=%.2f | Text=%s%n",
                        p.getXDirAdj(),
                        p.getYDirAdj(),
                        p.getUnicode()
                    );
                }
            }

            System.out.println("===== END LOCATION POSITIONS =====");

            System.out.println("\n===== LOCATION COLUMN START =====");

            for (TextPosition p : characters) {
                float x = p.getXDirAdj();
                double y = p.getYDirAdj();

                if (x >= 300 && x < 500 && y >= 70 && y <= 100) {
                    System.out.printf(
                        "X=%.2f | Y=%.2f | Text=%s%n",
                        x,
                        y,
                        p.getUnicode()
                    );
                }
            }

            System.out.println("===== END LOCATION COLUMN START =====");

            System.out.println("\n===== CPO NAME AND COORDINATES BY ROW =====");

            for (double y : yPositions) {
                if (y < 70) {
                    continue;
                }

                StringBuilder cpoName = new StringBuilder();
                StringBuilder latitude = new StringBuilder();
                StringBuilder longitude = new StringBuilder();

                for (TextPosition p : characters) {
                    if (Math.abs(p.getYDirAdj() - y) > 0.5) {
                        continue;
                    }

                    float x = p.getXDirAdj();

                    if (x < 82) {
                        cpoName.append(p.getUnicode());
                    } else if (x >= 500 && x < 538) {
                        latitude.append(p.getUnicode());
                    } else if (x >= 538 && x < 570) {
                        longitude.append(p.getUnicode());
                    }
                }

                System.out.printf(
                    "Y=%.2f | CPO=%s | Lat=%s | Lon=%s%n",
                    y, cpoName, latitude, longitude
                );
            }

            System.out.println("===== END CPO COORDINATES =====");

            System.out.println("\n===== FIRST STATION PREVIEW =====");

            double targetY = 79.76;
            StringBuilder cpo = new StringBuilder();
            StringBuilder location = new StringBuilder();
            StringBuilder latitude = new StringBuilder();
            StringBuilder longitude = new StringBuilder();

            for (TextPosition p : characters) {
                if (p.getYDirAdj() < 75 || p.getYDirAdj() > 82) {
                    continue;
                }

                float x = p.getXDirAdj();

                if (x < 82) {
                    cpo.append(p.getUnicode());
                } else if (x >= 300 && x < 500) {
                    location.append(p.getUnicode());
                } else if (x >= 500 && x < 538) {
                    latitude.append(p.getUnicode());
                } else if (x >= 538 && x < 570) {
                    longitude.append(p.getUnicode());
                }
            }

            System.out.println("CPO Name  : " + cpo);
            System.out.println("Location  : " + location);
            System.out.println("Latitude  : " + latitude);
            System.out.println("Longitude : " + longitude);

            System.out.println("===== END FIRST STATION PREVIEW =====");

            characters.sort(
                Comparator.comparingDouble(TextPosition::getYDirAdj)
                    .thenComparingDouble(TextPosition::getXDirAdj)
            );

            StringBuilder output = new StringBuilder();

            System.out.println("Total characters collected: " + characters.size());
            List<List<TextPosition>> rows = new ArrayList<>();

            for (TextPosition p : characters) {
                double y = p.getYDirAdj();

                List<TextPosition> matchingRow = null;

                for (List<TextPosition> existingRow : rows) {
                    double rowY = existingRow.stream()
                            .mapToDouble(TextPosition::getYDirAdj)
                            .average()
                            .orElse(y);

                    if (Math.abs(rowY - y) <= 3.5) {
                        matchingRow = existingRow;
                        break;
                    }
                }

                if (matchingRow == null) {
                    matchingRow = new ArrayList<>();
                    rows.add(matchingRow);
                }

                matchingRow.add(p);
            }

            rows.sort(Comparator.comparingDouble(row ->
                    row.stream()
                            .mapToDouble(TextPosition::getYDirAdj)
                            .average()
                            .orElse(0)));

            for (List<TextPosition> row : rows) {
                row.sort(Comparator.comparingDouble(TextPosition::getXDirAdj));

                StringBuilder line = new StringBuilder();

                for (TextPosition p : row) {
                    line.append(String.format(
                            "%.2f:%s ",
                            p.getXDirAdj(),
                            p.getUnicode()
                    ));
                }

                String rowText = line.toString().trim();

                if (!rowText.isBlank()) {
                    output.append(String.format(
                            "Y=%.2f | %s%n",
                            row.stream()
                                    .mapToDouble(TextPosition::getYDirAdj)
                                    .average()
                                    .orElse(0),
                            rowText
                    ));
                }
            }

            Files.writeString(
                outputPath,
                output.toString(),
                StandardCharsets.UTF_8
            );

            System.out.println("Preview saved to: " + outputPath);
            System.out.println("Preview rows: " + output.toString().lines().count());
            System.out.println(output);
        }
    }
}
