package com.karan.ev_charging_backend.dataimport;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

public class PdfPositionInspector extends PDFTextStripper {

    private final List<TextPosition> characters = new ArrayList<>();

    public PdfPositionInspector() throws IOException {
        super();
    }

    @Override
    protected void writeString(
            String text, List<TextPosition> positions) {

        characters.addAll(positions);
    }

    public static void main(String[] args) throws IOException {

        File pdfFile = new File(
            "C:/Users/Asus-pc/Downloads/Ev_charging_station_data.pdf"
        );

        try (PDDocument document = Loader.loadPDF(pdfFile)) {

            PdfPositionInspector inspector =
                    new PdfPositionInspector();

            inspector.setSortByPosition(true);
            inspector.setStartPage(1);
            inspector.setEndPage(1);
            inspector.getText(document);

            System.out.println("\n===== LATITUDE/LONGITUDE AREA =====");

            inspector.characters.stream()
                .filter(p -> p.getXDirAdj() >= 495 && p.getXDirAdj() <= 580)
                .filter(p -> p.getYDirAdj() >= 70 && p.getYDirAdj() <= 125)
                .sorted(
                    Comparator.comparingDouble(TextPosition::getYDirAdj)
                        .thenComparingDouble(TextPosition::getXDirAdj)
                )
                .forEach(p -> System.out.printf(
                    "X=%.2f | Y=%.2f | Text=%s%n",
                    p.getXDirAdj(),
                    p.getYDirAdj(),
                    p.getUnicode()
                ));

            if (true) return;

            inspector.characters.sort(
                Comparator.comparingDouble(TextPosition::getYDirAdj)
                    .thenComparingDouble(TextPosition::getXDirAdj)
            );

            System.out.println("Total characters collected: "
                    + inspector.characters.size());

            System.out.println("\n===== VISUAL ROWS =====");

            List<List<TextPosition>> rows = new ArrayList<>();

            for (TextPosition character : inspector.characters) {
                double y = character.getYDirAdj();

                List<TextPosition> matchingRow = null;

                for (List<TextPosition> row : rows) {
                    double rowY = row.get(0).getYDirAdj();

                    if (Math.abs(rowY - y) <= 3.5) {
                        matchingRow = row;
                        break;
                    }
                }

                if (matchingRow == null) {
                    matchingRow = new ArrayList<>();
                    rows.add(matchingRow);
                }

                matchingRow.add(character);
            }

            rows.sort(Comparator.comparingDouble(
                row -> row.stream()
                    .mapToDouble(TextPosition::getYDirAdj)
                    .average()
                    .orElse(0)
            ));

            int rowNumber = 0;

            for (List<TextPosition> row : rows) {
                row.sort(Comparator.comparingDouble(
                    TextPosition::getXDirAdj
                ));

                StringBuilder line = new StringBuilder();
                double previousEndX = -1;

                for (TextPosition character : row) {
                    double x = character.getXDirAdj();

                    if (previousEndX >= 0 && x - previousEndX > 2.5) {
                        line.append(' ');
                    }

                    line.append(character.getUnicode());
                    previousEndX = x + character.getWidthDirAdj();
                }

                System.out.printf(
                    "Row %02d | Y=%.2f | %s%n",
                    ++rowNumber,
                    row.stream()
                        .mapToDouble(TextPosition::getYDirAdj)
                        .average()
                        .orElse(0),
                    line.toString().trim()
                );
            }
        }
    }
}
