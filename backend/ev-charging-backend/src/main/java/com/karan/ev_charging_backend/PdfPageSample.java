
package com.karan.ev_charging_backend;

import java.io.File;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

public class PdfPageSample {

    public static void main(String[] args) throws Exception {

        File file = new File(
            "C:\\Users\\Asus-pc\\Downloads\\Ev_charging_station_data.pdf"
        );

        try (PDDocument document = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();

            // Print pages 3 to 5 to inspect actual record structure.
            stripper.setStartPage(3);
            stripper.setEndPage(5);

            java.nio.file.Files.writeString(
            java.nio.file.Path.of("pdf-page-3-sample.txt"),
            stripper.getText(document)
          );
            System.out.println("Page sample saved successfully.");
        }
    }
}

