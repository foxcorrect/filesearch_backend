package com.resume.service;

import com.resume.exception.BusinessException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class PdfExtractionService {

    private static final Logger log = LoggerFactory.getLogger(PdfExtractionService.class);

    /**
     * Extract text content from a PDF file.
     * Writes to a temp file first to avoid loading the entire file into
     * heap memory as a byte array, then deletes the temp file.
     */
    public String extractText(MultipartFile file) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("resume-upload-", ".pdf");
            file.transferTo(tempFile.toFile());

            try (PDDocument document = Loader.loadPDF(tempFile.toFile())) {
                PDFTextStripper stripper = new PDFTextStripper();
                return stripper.getText(document);
            }
        } catch (IOException e) {
            throw new BusinessException(400, "PDF文件解析失败: " + e.getMessage());
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    log.warn("Failed to delete temp PDF file: {}", tempFile, e);
                }
            }
        }
    }
}
