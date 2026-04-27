package com.resume.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class PdfLegacyTest {
    public static void main(String[] args) throws Exception {
        byte[] pdfData = Files.readAllBytes(Paths.get(
            System.getenv("USERPROFILE") + "/AppData/Local/Temp/test_heading.pdf"));
        StringBuilder out = new StringBuilder();
        try (PDDocument document = Loader.loadPDF(pdfData)) {
            PDFTextStripper stripper = new PDFTextStripper() {
                private final List<TextPosition> allTexts = new ArrayList<>();
                @Override
                protected void processTextPosition(TextPosition text) { allTexts.add(text); }
                @Override
                protected void writePage() throws IOException {
                    out.append("=== Per-character X positions ===\n");
                    for (TextPosition tp : allTexts) {
                        out.append(String.format("Y=%3d X=%4.0f W=%4.0f C='%s'%n",
                            Math.round(tp.getY()), tp.getX(), tp.getWidth(), tp.getUnicode()));
                    }
                    allTexts.clear();
                }
            };
            stripper.setSortByPosition(true);
            stripper.getText(document);
        }
        // Also run through service to see extraction
        PdfExtractionService service = new PdfExtractionService();
        String result = service.extractText(pdfData);
        out.append("\n=== Service extraction ===\n");
        out.append(result);
        out.append("\n\n=== Lines ===\n");
        for (String line : result.split("\n")) {
            out.append("  '").append(line).append("'\n");
        }
        Files.writeString(Paths.get("E:/code/filesearch/resume-backend/analyze_legacy_heading.txt"), out.toString());
        System.out.println("Done");
    }
}
