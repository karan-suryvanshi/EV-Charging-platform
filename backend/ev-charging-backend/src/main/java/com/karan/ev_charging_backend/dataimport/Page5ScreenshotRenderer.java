package com.karan.ev_charging_backend.dataimport;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.rendering.PDFRenderer;

public class Page5ScreenshotRenderer {

    public static void main(String[] args) throws IOException {
        Path pdfPath = Path.of("C:/Users/Asus-pc/Downloads/Ev_charging_station_data.pdf");

        // Paths to save images
        File downloadCropFile = new File("C:/Users/Asus-pc/Downloads/page5_hsd_millennium_crop.png");
        File artifactCropFile = new File("C:/Users/Asus-pc/.gemini/antigravity-cli/brain/8e39fa7f-cb71-4b6e-9d37-d0f7c5540558/page5_hsd_millennium_crop.png");

        File downloadFullFile = new File("C:/Users/Asus-pc/Downloads/page5_full.png");
        File artifactFullFile = new File("C:/Users/Asus-pc/.gemini/antigravity-cli/brain/8e39fa7f-cb71-4b6e-9d37-d0f7c5540558/page5_full.png");

        // Ensure artifact parent dir exists
        artifactCropFile.getParentFile().mkdirs();

        try (PDDocument document = Loader.loadPDF(pdfPath.toFile())) {
            // Page 5 is index 4
            int pageIndex = 4;
            PDPage page = document.getPage(pageIndex);
            PDRectangle mediaBox = page.getMediaBox();
            float pageWidth = mediaBox.getWidth();
            float pageHeight = mediaBox.getHeight();

            System.out.printf("Page 5 dimensions in PDF points: %.2f x %.2f%n", pageWidth, pageHeight);

            PDFRenderer renderer = new PDFRenderer(document);
            // Render at 300 DPI for ultra clear readability
            float dpi = 300.0f;
            float scale = dpi / 72.0f;
            System.out.println("Rendering Page 5 at " + dpi + " DPI (scale " + scale + ")...");
            BufferedImage fullImage = renderer.renderImageWithDPI(pageIndex, dpi);

            System.out.printf("Rendered image size: %d x %d pixels%n", fullImage.getWidth(), fullImage.getHeight());

            // Save full page
            ImageIO.write(fullImage, "png", downloadFullFile);
            ImageIO.write(fullImage, "png", artifactFullFile);
            System.out.println("Saved full page to: " + downloadFullFile.getAbsolutePath());

            // On Page 5:
            // Table Header is around Y = 55 to 70 pt.
            // Record 1 (Dharmavaram) is at Y = 76.90 pt.
            // Record 2 (Hsd Millennium 1) is at Y = 85.03 pt.
            // Record 3 (Hsd Millennium 2) is at Y = 93.16 pt.
            // Record 4 (Hsd Millennium 3) is at Y = 101.30 pt.
            // Record 5 is at Y = 109.43 pt.
            // Record 6 is at Y = 117.56 pt.
            // Record 7 is at Y = 125.69 pt.
            //
            // Let's crop from Y = 40 pt to Y = 150 pt across full width:
            int cropY = (int) Math.max(0, 45.0f * scale);
            int cropHeight = (int) (110.0f * scale); // 110 pt height covers header and top 8 records
            int cropX = (int) (30.0f * scale);
            int cropWidth = (int) ((pageWidth - 40.0f) * scale);

            if (cropY + cropHeight > fullImage.getHeight()) {
                cropHeight = fullImage.getHeight() - cropY;
            }
            if (cropX + cropWidth > fullImage.getWidth()) {
                cropWidth = fullImage.getWidth() - cropX;
            }

            System.out.printf("Cropping region: x=%d, y=%d, w=%d, h=%d%n", cropX, cropY, cropWidth, cropHeight);
            BufferedImage cropImage = fullImage.getSubimage(cropX, cropY, cropWidth, cropHeight);

            ImageIO.write(cropImage, "png", downloadCropFile);
            ImageIO.write(cropImage, "png", artifactCropFile);
            System.out.println("Saved cropped snippet to: " + downloadCropFile.getAbsolutePath());
            System.out.println("Saved artifact snippet to: " + artifactCropFile.getAbsolutePath());
        }
    }
}
