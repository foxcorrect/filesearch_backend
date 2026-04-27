package com.resume.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Standalone test for PdfExtractionService that doesn't require Spring context.
 */
public class PdfExtractionStandaloneTest {

    static int passed = 0;
    static int failed = 0;

    public static void main(String[] args) throws IOException {
        PdfExtractionService service = new PdfExtractionService();

        // Test 1: Heading PDF with centered title + 2 columns
        testHeadingPdf(service);

        // Test 2: Multi-column PDF (no heading)
        testMultiColumnPdf(service);

        // Test 3: Original multi-column PDF (single BT/ET block, relative Td)
        testLegacyMultiColumnPdf(service);

        // Test 4: Original heading PDF (single BT/ET block)
        testLegacyHeadingPdf(service);

        System.out.println("\n========================================");
        System.out.println("Results: " + passed + " passed, " + failed + " failed");
    }

    static void check(String name, boolean condition) {
        if (condition) {
            System.out.println("  PASS: " + name);
            passed++;
        } else {
            System.out.println("  FAIL: " + name);
            failed++;
        }
    }

    static void testHeadingPdf(PdfExtractionService service) throws IOException {
        System.out.println("=== Test 1: Proper Heading PDF ===");
        byte[] pdfData = Files.readAllBytes(Paths.get(
            System.getenv("USERPROFILE") + "/AppData/Local/Temp/test_proper_heading.pdf"));
        String result = service.extractText(pdfData);
        System.out.println(result);
        System.out.println("===========================");

        check("Title present", result.contains("Resume - John Smith"));
        check("Column 1 present", result.contains("Column 1 - Name: John Smith"));
        check("Column 2 present", result.contains("Column 2 - Education: MIT"));
        check("Column separator", result.contains("---"));

        int titleIdx = result.indexOf("Resume - John Smith");
        int col1Idx = result.indexOf("Column 1 - Name");
        int col2Idx = result.indexOf("Column 2 - Education");
        check("Title before col1", titleIdx < col1Idx);
        check("Title before col2", titleIdx < col2Idx);
        check("Title not mixed in col1",
            !result.substring(col1Idx, col1Idx + 50).contains("Resume"));
    }

    static void testMultiColumnPdf(PdfExtractionService service) throws IOException {
        System.out.println("\n=== Test 2: Proper Multi-column PDF ===");
        byte[] pdfData = Files.readAllBytes(Paths.get(
            System.getenv("USERPROFILE") + "/AppData/Local/Temp/test_proper_multi.pdf"));
        String result = service.extractText(pdfData);
        System.out.println(result);
        System.out.println("================================");

        check("Column 1 content present", result.contains("Column 1 - Name: John Smith"));
        check("Column 2 content present", result.contains("Column 2 - Education: MIT University"));
        check("Column separator", result.contains("---"));

        // Verify columns are not mixed on the same line
        for (String line : result.split("\n")) {
            check("No mixed columns on: " + line,
                !(line.contains("Column 1") && line.contains("Column 2")));
        }
    }

    static void testLegacyMultiColumnPdf(PdfExtractionService service) throws IOException {
        System.out.println("\n=== Test 3: Legacy Multi-column PDF (relative Td) ===");
        byte[] pdfData = Files.readAllBytes(Paths.get(
            System.getenv("USERPROFILE") + "/AppData/Local/Temp/test_multi2.pdf"));
        String result = service.extractText(pdfData);
        System.out.println(result);
        System.out.println("================================");

        // Legacy PDF has content at different Y values, so it won't detect columns
        // But content should still be readable in correct order
        check("Content not empty", !result.isEmpty());
        check("Col1 name present", result.contains("Column 1 - Name: John Smith"));
        check("Col2 edu present", result.contains("Column 2 - Education: MIT University"));
    }

    static void testLegacyHeadingPdf(PdfExtractionService service) throws IOException {
        System.out.println("\n=== Test 4: Legacy Heading PDF (relative Td) ===");
        byte[] pdfData = Files.readAllBytes(Paths.get(
            System.getenv("USERPROFILE") + "/AppData/Local/Temp/test_heading.pdf"));
        String result = service.extractText(pdfData);
        System.out.println(result);
        System.out.println("================================");

        check("Content not empty", !result.isEmpty());
        check("Title present", result.contains("Resume - John Smith"));
        check("Col1 name present", result.contains("Column 1 - Name: John Smith"));
        check("Col2 edu present", result.contains("Column 2 - Education: MIT"));
    }
}
