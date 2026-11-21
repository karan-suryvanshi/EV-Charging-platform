package com.karan.ev_charging_backend.dataimport;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

public class PdfTextExtractor {

    public static void main(String[] args) {

        String pdfPath = "C:/Users/Asus-pc/Downloads/Ev_charging_station_data.pdf";

        try (PDDocument document = Loader.loadPDF(new File(pdfPath))) {

            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);

            Path outputPath = Path.of(
                "C:/Users/Asus-pc/Downloads/ev_stations_extracted.txt"
            );

            Files.writeString(outputPath, text, StandardCharsets.UTF_8);

            System.out.println("Extracted text saved to: " + outputPath);

            System.out.println("Total pages: " + document.getNumberOfPages());
            System.out.println("Extracted text length: " + text.length());
            System.out.println("First 3000 characters:");
            System.out.println(text.substring(0, Math.min(3000, text.length())));

        } catch (IOException e) {
            System.err.println("PDF reading failed: " + e.getMessage());
        }
    }
}
