package com.resume.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class PdfMultiTest {
    public static void main(String[] args) throws Exception {
        String[] pdfs = {"test_proper_heading.pdf", "test_proper_multi.pdf"};
        for (String name : pdfs) {
            byte[] pdfData = Files.readAllBytes(Paths.get(
                System.getenv("USERPROFILE") + "/AppData/Local/Temp/" + name));
            analyze(name, pdfData);
        }
    }

    static void analyze(String name, byte[] pdfData) throws Exception {
        StringBuilder out = new StringBuilder();
        out.append("=== " + name + " ===\n");

        try (PDDocument document = Loader.loadPDF(pdfData)) {
            PDFTextStripper stripper = new PDFTextStripper() {
                private final List<TextPosition> allTexts = new ArrayList<>();

                @Override
                protected void processTextPosition(TextPosition text) {
                    allTexts.add(text);
                }

                @Override
                protected void writePage() throws IOException {
                    Map<Float, List<TextPosition>> yGroups = new TreeMap<>();
                    for (TextPosition tp : allTexts) {
                        float y = Math.round(tp.getY());
                        yGroups.computeIfAbsent(y, k -> new ArrayList<>()).add(tp);
                    }

                    for (Map.Entry<Float, List<TextPosition>> entry : yGroups.entrySet()) {
                        List<TextPosition> line = entry.getValue();
                        line.sort(Comparator.comparing(TextPosition::getX));

                        float minX = Float.MAX_VALUE, maxX = Float.MIN_VALUE;
                        StringBuilder text = new StringBuilder();
                        for (TextPosition tp : line) {
                            minX = Math.min(minX, tp.getX());
                            maxX = Math.max(maxX, tp.getX() + tp.getWidth());
                            text.append(tp.getUnicode());
                        }

                        out.append(String.format("Y=%3d  X=%4.0f-%4.0f | %s%n",
                            Math.round(entry.getKey()), minX, maxX, text.toString().trim()));
                    }
                    allTexts.clear();
                }
            };
            stripper.setSortByPosition(true);
            stripper.getText(document);
        }

        Files.writeString(Paths.get("E:/code/filesearch/resume-backend/analyze_" + name.replace(".pdf", ".txt")), out.toString());
        System.out.println("Analyzed " + name);
    }
}
