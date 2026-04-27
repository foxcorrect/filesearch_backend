package com.resume.service;

import java.nio.file.Files;
import java.nio.file.Paths;

public class PdfDebugTest {
    public static void main(String[] args) throws Exception {
        PdfExtractionService service = new PdfExtractionService();
        byte[] pdfData = Files.readAllBytes(Paths.get("E:/code/filesearch/resume-backend/陈媛.pdf"));
        String result = service.extractText(pdfData);
        Files.writeString(Paths.get("E:/code/filesearch/resume-backend/output_result.txt"), result);
        System.out.println("=== Full result ===");
        System.out.println(result);
        System.out.println("=== END ===");
        System.out.println("\n=== Debug: show line structure ===");
        String[] lines = result.split("\n");
        for (int i = 0; i < lines.length; i++) {
            System.out.println("L" + i + ": '" + lines[i] + "'");
        }
    }
}
