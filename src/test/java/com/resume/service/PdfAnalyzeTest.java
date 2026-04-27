package com.resume.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class PdfAnalyzeTest {
    public static void main(String[] args) throws Exception {
        byte[] pdfData = Files.readAllBytes(Paths.get("E:/code/filesearch/resume-backend/陈媛.pdf"));

        StringBuilder out = new StringBuilder();

        try (PDDocument document = Loader.loadPDF(pdfData)) {
            out.append("=== Text positions (grouped by Y) ===\n");
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

                        out.append(String.format("Y=%3d  X=%4.0f-%4.0f (span=%4.0f) | %s%n",
                            Math.round(entry.getKey()), minX, maxX, maxX - minX, text.toString().trim()));

                        for (TextPosition tp : line) {
                            float fs = tp.getFontSize();
                            float w = tp.getWidth();
                            out.append(String.format("         X=%4.0f W=%4.0f FS=%2.0f '%s'%n",
                                tp.getX(), w, fs, tp.getUnicode()));
                        }
                    }
                    allTexts.clear();
                }
            };
            stripper.setSortByPosition(true);
            String result = stripper.getText(document);
            out.append("\n=== Full extraction ===\n");
            out.append(result);
        }

        Files.writeString(Paths.get("E:/code/filesearch/resume-backend/analyze_output.txt"), out.toString());
        System.out.println("Analysis written to analyze_output.txt");
    }
}
