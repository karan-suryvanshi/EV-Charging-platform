
package com.karan.ev_charging_backend;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

public class PdfInspection {

    public static void main(String[] args) throws Exception {
        File file = new File(
            "C:\\Users\\Asus-pc\\Downloads\\Ev_charging_station_data.pdf"
        );

        Pattern coordinates = Pattern.compile(
            "(?<![\\d.])(-?\\d{1,2}\\.\\d{4,})\\s+(-?\\d{1,3}\\.\\d{4,})(?![\\d.])"
        );

        long coordinateRecords = 0;
        Set<String> uniqueCoordinates = new HashSet<>();

        try (PDDocument document = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            Matcher matcher = coordinates.matcher(text);

            while (matcher.find()) {
                double latitude = Double.parseDouble(matcher.group(1));
                double longitude = Double.parseDouble(matcher.group(2));

                if (latitude >= -90 && latitude <= 90
                        && longitude >= -180 && longitude <= 180) {
                    coordinateRecords++;

                    uniqueCoordinates.add(
                        String.format(java.util.Locale.ROOT,
                            "%.6f,%.6f", latitude, longitude)
                    );
                }
            }

            System.out.println("Total PDF pages: " + document.getNumberOfPages());
            System.out.println("Records with valid coordinate pairs: "
                    + coordinateRecords);
            System.out.println("Unique coordinate pairs: "
                    + uniqueCoordinates.size());
            System.out.println("Repeated coordinate occurrences: "
                    + (coordinateRecords - uniqueCoordinates.size()));
        }
    }
}
