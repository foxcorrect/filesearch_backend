package com.resume.service;

import com.resume.exception.BusinessException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.fontbox.ttf.TrueTypeCollection;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

@Service
public class PdfExtractionService {

    private static final Logger log = LoggerFactory.getLogger(PdfExtractionService.class);

    /**
     * Generate a styled PDF from HTML content using Flying Saucer renderer.
     * Applies CSS styling for a professional resume look while preserving
     * the original formatting structure (headings, bold, lists, etc.).
     */
    public byte[] generateStyledPdf(String htmlContent) {
        if (htmlContent == null || htmlContent.isBlank()) {
            throw new BusinessException(400, "内容为空，无法生成PDF");
        }

        String styledHtml = buildStyledHtml(htmlContent);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(styledHtml);
            renderer.layout();
            renderer.createPDF(baos, false); // false = don't close the stream
            renderer.finishPDF();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Styled PDF generation failed, falling back to plain text: {}", e.getMessage());
            // Fallback: generate plain-text PDF
            return generatePdf(htmlContent);
        }
    }

    /**
     * Wrap Quill HTML content in a complete HTML document with CSS styling
     * that produces a clean, professional resume PDF layout.
     */
    private String buildStyledHtml(String content) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
            <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
            <style>
                @page {
                    size: A4;
                    margin: 20mm 18mm;
                }
                body {
                    font-family: SimSun, SimHei, 'Microsoft YaHei', serif;
                    font-size: 11pt;
                    line-height: 1.7;
                    color: #222;
                }
                h1 {
                    font-size: 20pt;
                    text-align: center;
                    margin: 8pt 0 14pt 0;
                    font-weight: bold;
                }
                h2 {
                    font-size: 14pt;
                    margin: 18pt 0 8pt 0;
                    padding-bottom: 4pt;
                    border-bottom: 1px solid #999;
                    font-weight: bold;
                    color: #1a1a1a;
                }
                h3 {
                    font-size: 12pt;
                    margin: 12pt 0 6pt 0;
                    font-weight: bold;
                }
                p {
                    margin: 4pt 0 8pt 0;
                    text-indent: 0;
                }
                ul, ol {
                    margin: 4pt 0 8pt 0;
                    padding-left: 22pt;
                }
                li {
                    margin: 2pt 0;
                }
                strong, b {
                    font-weight: bold;
                }
                em, i {
                    font-style: italic;
                }
                u {
                    text-decoration: underline;
                }
                blockquote {
                    margin: 6pt 4pt;
                    padding: 4pt 12pt;
                    border-left: 3px solid #ccc;
                    color: #555;
                }
                pre, code {
                    font-family: SimSun, serif;
                    font-size: 10pt;
                    background: #f5f5f5;
                    padding: 1pt 3pt;
                }
                img {
                    max-width: 100%;
                }
                .ql-align-center { text-align: center; }
                .ql-align-right { text-align: right; }
                .ql-align-justify { text-align: justify; }
            </style>
            </head>
            <body>
            """ + content + """
            </body>
            </html>
            """;
    }

    /**
     * Generate a simple PDF document from HTML-rich text content.
     * Strips HTML tags and creates a basic PDF with the plain text.
     * Kept as fallback for when styled generation fails.
     */
    public byte[] generatePdf(String htmlContent) {
        String plainText = htmlToPlainText(htmlContent);

        try {
            return generatePdfFromPlainText(plainText);
        } catch (RuntimeException e) {
            // Log first 200 chars for debugging
            String sample = plainText.substring(0, Math.min(200, plainText.length()));
            log.error("PDF generation failed. Content sample (first 200 chars): [{}]", sample, e);
            throw new BusinessException(500, "PDF生成失败：内容包含PDF不支持的字符，请移除特殊符号或表情后重试");
        }
    }

    private byte[] generatePdfFromPlainText(String plainText) {

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDType0Font font = loadChineseFont(document);

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                cs.setFont(font, 11);
                cs.setLeading(16f);
                cs.beginText();
                cs.newLineAtOffset(50, 780);

                for (String line : plainText.split("\n")) {
                    if (line.trim().isEmpty()) {
                        cs.newLine();
                        continue;
                    }
                    String text = line.trim();
                    // Use word wrapping at ~50 CJK chars per line (rough A4 width)
                    int maxLen = 50;
                    while (text.length() > maxLen) {
                        cs.showText(text.substring(0, maxLen));
                        cs.newLine();
                        text = text.substring(maxLen);
                    }
                    cs.showText(text);
                    cs.newLine();
                }
                cs.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(500, "PDF生成失败: " + e.getMessage());
        }
    }

    /**
     * Convert HTML content to plain text for PDF generation.
     * Uses regex-based tag stripping with comprehensive character filtering
     * to ensure PDFBox can render all remaining characters with CJK fonts.
     */
    private String htmlToPlainText(String html) {
        if (html == null || html.isEmpty()) return "";

        // 1. Replace <br> tags with newlines first
        String text = html.replaceAll("<br\\s*/?>", "\n");

        // 2. Replace all remaining HTML tags with newlines to preserve paragraph structure
        text = text.replaceAll("<[^>]+>", "\n");

        // 3. Normalize multiple consecutive newlines to double newlines (paragraph breaks)
        text = text.replaceAll("\\n{3,}", "\n\n");

        // 4. Decode common HTML entities
        text = text.replaceAll("&amp;", "&")
                   .replaceAll("&lt;", "<")
                   .replaceAll("&gt;", ">")
                   .replaceAll("&quot;", "\"")
                   .replaceAll("&#39;", "'")
                   .replaceAll("&#x27;", "'")
                   .replaceAll("&#x2F;", "/")
                   .replaceAll("&#x60;", "`")
                   .replaceAll("&#x3C;", "<")
                   .replaceAll("&#x3E;", ">")
                   .replaceAll("&nbsp;", " ")
                   .replaceAll("&ensp;", " ")
                   .replaceAll("&emsp;", " ")
                   .replaceAll("&ndash;", "-")
                   .replaceAll("&mdash;", "-")
                   .replaceAll("&laquo;", "\"")
                   .replaceAll("&raquo;", "\"")
                   .replaceAll("&bull;", "*")
                   .replaceAll("&hellip;", "...");

        // 5. Non-breaking space (both forms)
        text = text.replaceAll("\\u00a0", " ");

        // 6. Remove control characters, private use area, and other PDFBox-problematic chars
        //    C0 controls (except \t \n \r), C1 controls, private use, specials block
        text = text.replaceAll("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F-\\u009F\\u00AD\\u034F\\u061C\\u070F\\u180B-\\u180E\\u200B-\\u200F\\u2028-\\u202F\\u205F-\\u206F\\uFE00-\\uFE0F\\uFEFF\\uFFF0-\\uFFFF]", "");

        // 7. Remove surrogate pairs and characters outside BMP that CJK fonts don't support (emoji, etc.)
        text = text.replaceAll("[\\uD800-\\uDFFF]", "");

        // 8. Remove private use area (common in rich text editor output)
        text = text.replaceAll("[\\uE000-\\uF8FF]", "");

        // 9. Filter to characters commonly supported by CJK fonts.
        //    Replace unsupported chars with space to prevent PDFBox from throwing.
        text = filterSupportedChars(text);

        // 10. Collapse multiple spaces/tabs into single space
        text = text.replaceAll("[ \\t]+", " ");

        // 11. Collapse blank lines (lines that contain only spaces)
        text = text.replaceAll("(?m)^[ \\t]+$", "");

        return text.trim();
    }

    /**
     * Keep only characters that are commonly supported by Windows CJK fonts
     * (SimSun, SimHei, SimFang, KaiTi, Microsoft YaHei, etc.).
     * Replace unsupported characters with space.
     */
    private String filterSupportedChars(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int cp = c;
            // Keep \t, \n, \r
            if (cp == 0x09 || cp == 0x0A || cp == 0x0D) {
                sb.append(c);
                continue;
            }
            if (isCjkFontSupported(cp)) {
                sb.append(c);
            } else {
                sb.append(' ');
            }
        }
        return sb.toString();
    }

    /**
     * Check if a Unicode code point is commonly supported by CJK fonts on Windows.
     * Covers ASCII, Latin-1 Supplement, CJK ideographs, CJK punctuation,
     * fullwidth forms, hangul, kana, bopomofo, and common symbols/punctuation.
     */
    private boolean isCjkFontSupported(int cp) {
        // ASCII printable (0x20-0x7E)
        if (cp >= 0x20 && cp <= 0x7E) return true;
        // Latin-1 Supplement (0xA0-0xFF), excluding control chars (already removed)
        if (cp >= 0xA0 && cp <= 0xFF) return true;
        // General Punctuation: en/em dash, bullet, ellipsis, quotes, etc.
        if (cp >= 0x2000 && cp <= 0x206F) return true;
        // Superscripts & Subscripts
        if (cp >= 0x2070 && cp <= 0x209F) return true;
        // Currency Symbols
        if (cp >= 0x20A0 && cp <= 0x20CF) return true;
        // Letterlike Symbols
        if (cp >= 0x2100 && cp <= 0x214F) return true;
        // Number Forms
        if (cp >= 0x2150 && cp <= 0x218F) return true;
        // Arrows
        if (cp >= 0x2190 && cp <= 0x21FF) return true;
        // Mathematical Operators
        if (cp >= 0x2200 && cp <= 0x22FF) return true;
        // Miscellaneous Technical
        if (cp >= 0x2300 && cp <= 0x23FF) return true;
        // Enclosed Alphanumerics
        if (cp >= 0x2460 && cp <= 0x24FF) return true;
        // Box Drawing
        if (cp >= 0x2500 && cp <= 0x257F) return true;
        // Block Elements
        if (cp >= 0x2580 && cp <= 0x259F) return true;
        // Geometric Shapes
        if (cp >= 0x25A0 && cp <= 0x25FF) return true;
        // Miscellaneous Symbols (common)
        if (cp >= 0x2600 && cp <= 0x26FF) return true;
        // Dingbats (common)
        if (cp >= 0x2700 && cp <= 0x27BF) return true;
        // CJK Symbols and Punctuation
        if (cp >= 0x3000 && cp <= 0x303F) return true;
        // Hiragana
        if (cp >= 0x3040 && cp <= 0x309F) return true;
        // Katakana
        if (cp >= 0x30A0 && cp <= 0x30FF) return true;
        // Bopomofo
        if (cp >= 0x3100 && cp <= 0x312F) return true;
        // Enclosed CJK Letters and Months
        if (cp >= 0x3200 && cp <= 0x32FF) return true;
        // CJK Compatibility
        if (cp >= 0x3300 && cp <= 0x33FF) return true;
        // CJK Unified Ideographs Extension A
        if (cp >= 0x3400 && cp <= 0x4DBF) return true;
        // CJK Unified Ideographs
        if (cp >= 0x4E00 && cp <= 0x9FFF) return true;
        // Hangul Syllables
        if (cp >= 0xAC00 && cp <= 0xD7AF) return true;
        // CJK Compatibility Ideographs
        if (cp >= 0xF900 && cp <= 0xFAFF) return true;
        // Vertical Forms
        if (cp >= 0xFE10 && cp <= 0xFE1F) return true;
        // CJK Compatibility Forms
        if (cp >= 0xFE30 && cp <= 0xFE4F) return true;
        // Small Form Variants
        if (cp >= 0xFE50 && cp <= 0xFE6F) return true;
        // Halfwidth and Fullwidth Forms
        if (cp >= 0xFF00 && cp <= 0xFFEF) return true;
        return false;
    }

    private PDType0Font loadChineseFont(PDDocument document) throws IOException {
        // Try standalone TTF fonts first (directly loadable), then TTC collections
        String[] ttfCandidates = {
            "C:/Windows/Fonts/simfang.ttf",
            "C:/Windows/Fonts/simhei.ttf",
            "C:/Windows/Fonts/simkai.ttf",
            "C:/Windows/Fonts/simsunb.ttf",
        };
        for (String path : ttfCandidates) {
            File f = new File(path);
            if (f.exists()) {
                return PDType0Font.load(document, new java.io.FileInputStream(f), true);
            }
        }
        // Fallback: try TTC files with TrueTypeCollection
        String[] ttcCandidates = {
            "C:/Windows/Fonts/msyh.ttc",
            "C:/Windows/Fonts/simsun.ttc",
        };
        for (String path : ttcCandidates) {
            File f = new File(path);
            if (!f.exists()) continue;
            try (TrueTypeCollection ttc = new TrueTypeCollection(f)) {
                // Get first font in the collection by name extraction
                // Try common English names that PDFBox might find
                String[] names = {"MicrosoftYaHei", "Microsoft YaHei", "SimSun"};
                for (String name : names) {
                    TrueTypeFont ttf = ttc.getFontByName(name);
                    if (ttf != null) return PDType0Font.load(document, ttf, true);
                }
            }
        }
        throw new IOException("未找到支持中文的字体文件");
    }

    /**
     * Extract text content from a PDF file.
     * Writes to a temp file first to avoid loading the entire file into
     * heap memory as a byte array, then deletes the temp file.
     */
    public String extractText(byte[] fileData) {
        try (PDDocument document = Loader.loadPDF(fileData)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        } catch (IOException e) {
            throw new BusinessException(400, "PDF文件解析失败: " + e.getMessage());
        }
    }

}
