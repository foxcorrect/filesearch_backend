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
import org.apache.pdfbox.text.TextPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.lowagie.text.pdf.BaseFont;
import org.xhtmlrenderer.pdf.ITextFontResolver;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PdfExtractionService {

    private static final Logger log = LoggerFactory.getLogger(PdfExtractionService.class);

    private final String fontsDir;

    public PdfExtractionService(@Value("${pdf.fonts-dir:C:/Windows/Fonts}") String fontsDir) {
        this.fontsDir = fontsDir;
    }

    // Pattern to match <img src="data:image/...;base64,...">
    private static final Pattern DATA_URI_PATTERN =
        Pattern.compile("<img\\s+([^>]*?)src\\s*=\\s*\"data:image/([^;]+);base64,([^\"]+)\"([^>]*?)>", Pattern.CASE_INSENSITIVE);

    /**
     * Generate a styled PDF from HTML content using Flying Saucer renderer.
     * Applies CSS styling for a professional resume look while preserving
     * the original formatting structure (headings, bold, lists, etc.).
     */
    public byte[] generateStyledPdf(String htmlContent) {
        if (htmlContent == null || htmlContent.isBlank()) {
            throw new BusinessException(400, "内容为空，无法生成PDF");
        }

        // Extract base64 data URIs to temp files (Flying Saucer can't handle data: URIs)
        List<Path> tempFiles = new ArrayList<>();
        String processedContent = extractDataUrisToTempFiles(htmlContent, tempFiles);

        String styledHtml = buildStyledHtml(processedContent);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ITextRenderer renderer = new ITextRenderer();

            // Register Chinese fonts so CJK text renders in the PDF
            registerChineseFonts(renderer);

            // Set base URL to help Flying Saucer resolve file:// paths
            renderer.setDocumentFromString(styledHtml, "file:///");
            renderer.layout();
            renderer.createPDF(baos, false);
            renderer.finishPDF();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Styled PDF generation failed, falling back to plain text: {}", e.getMessage());
            return generatePdf(htmlContent);
        } finally {
            // Clean up temp image files
            for (Path p : tempFiles) {
                try { Files.deleteIfExists(p); } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Replace base64 data URIs in &lt;img&gt; tags with temporary file paths,
     * since Flying Saucer's PDF renderer does not support inline data URIs.
     * Returns the modified HTML and populates {@code tempFiles} with paths to clean up.
     */
    private String extractDataUrisToTempFiles(String html, List<Path> tempFiles) {
        Matcher m = DATA_URI_PATTERN.matcher(html);
        if (!m.find()) return html; // no data URIs, skip

        m.reset();
        StringBuffer sb = new StringBuffer(html.length());
        while (m.find()) {
            String ext = m.group(2).toLowerCase();
            // Map MIME extension to a file suffix Flying Saucer can resolve
            String suffix = switch (ext) {
                case "png" -> ".png";
                case "jpeg", "jpg" -> ".jpg";
                case "gif" -> ".gif";
                case "bmp" -> ".bmp";
                case "svg+xml" -> ".svg";
                default -> ".png"; // fallback
            };

            try {
                byte[] imageBytes = Base64.getDecoder().decode(m.group(3));
                Path tempFile = Files.createTempFile("resume_img_", suffix);
                Files.write(tempFile, imageBytes);
                tempFiles.add(tempFile);

                // Replace the data URI src with the temp file path
                String newImgTag = "<img " + m.group(1) + "src=\"file:///" +
                    tempFile.toAbsolutePath().toString().replace('\\', '/') + "\" " + m.group(4) + ">";
                m.appendReplacement(sb, Matcher.quoteReplacement(newImgTag));
            } catch (IllegalArgumentException e) {
                log.warn("Failed to decode base64 image data, skipping: {}", e.getMessage());
                m.appendReplacement(sb, "$0"); // keep original
            } catch (IOException e) {
                log.warn("Failed to write temp image file, skipping: {}", e.getMessage());
                m.appendReplacement(sb, "$0"); // keep original
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Register Windows CJK fonts so Flying Saucer can render Chinese text.
     * Uses IDENTITY_H encoding so Unicode/CJK characters map correctly to glyphs,
     * and embeds font subsets for portable PDF rendering.
     */
    private void registerChineseFonts(ITextRenderer renderer) {
        ITextFontResolver resolver = (ITextFontResolver) renderer.getFontResolver();

        // SimHei (黑体) — register as both specific family and generic serif/sans-serif
        registerFont(resolver, fontsDir + "/simhei.ttf", "SimHei");
        registerFont(resolver, fontsDir + "/simhei.ttf", "serif");
        registerFont(resolver, fontsDir + "/simhei.ttf", "sans-serif");

        // FangSong (仿宋) — simfang.ttf's internal family name
        registerFont(resolver, fontsDir + "/simfang.ttf", "FangSong");

        // KaiTi (楷体)
        registerFont(resolver, fontsDir + "/simkai.ttf", "KaiTi");

        // SimSun (宋体) from TTC collection — broader character coverage
        registerFont(resolver, fontsDir + "/simsun.ttc,0", "SimSun");
        registerFont(resolver, fontsDir + "/simsun.ttc,0", "serif");
        registerFont(resolver, fontsDir + "/simsun.ttc,0", "sans-serif");

        // Microsoft YaHei from TTC
        registerFont(resolver, fontsDir + "/msyh.ttc,0", "Microsoft YaHei");
    }

    private void registerFont(ITextFontResolver resolver, String path, String family) {
        try {
            resolver.addFont(path, family, BaseFont.IDENTITY_H, true, null);
            log.debug("Registered font: {} as {}", path, family);
        } catch (Exception e) {
            log.debug("Font unavailable (non-fatal): {} as {} — {}", path, family, e.getMessage());
        }
    }

    /**
     * Wrap HTML editor content in a complete HTML document with CSS styling
     * that produces a clean, professional resume PDF layout.
     * Converts content to XHTML for Flying Saucer compatibility.
     */
    private String buildStyledHtml(String content) {
        // Normalize special characters for reliable font rendering
        String normalized = normalizeContent(content);
        // Ensure XHTML compliance for Flying Saucer's XML parser
        String xhtmlContent = ensureXhtmlCompliant(normalized);
        return """
            <!DOCTYPE html>
            <html>
            <head>
            <meta http-equiv="Content-Type" content="text/html; charset=UTF-8"/>
            <style>
                @page {
                    size: A4;
                    margin: 20mm 18mm;
                }
                body {
                    font-family: SimHei, SimSun, FangSong, KaiTi, 'Microsoft YaHei', serif;
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
                table {
                    width: 100%;
                    border-collapse: collapse;
                    margin: 8pt 0;
                }
                table td, table th {
                    border: 1px solid #ccc;
                    padding: 6pt 8pt;
                    text-align: left;
                }
                img {
                    max-width: 100%;
                }
                /* Backward compatibility: Quill alignment classes */
                .ql-align-center { text-align: center; }
                .ql-align-right { text-align: right; }
                .ql-align-justify { text-align: justify; }
            </style>
            </head>
            <body>
            """ + xhtmlContent + """
            </body>
            </html>
            """;
    }

    /**
     * Convert HTML content to XHTML-compliant format for Flying Saucer's XML parser.
     * Self-closes void elements like &lt;br&gt;, &lt;hr&gt;, &lt;img&gt;.
     */
    private String ensureXhtmlCompliant(String html) {
        if (html == null || html.isEmpty()) return html;

        // 1. Remove img tags without a valid src (empty src or missing src)
        //    Flying Saucer would render these as blank/placeholder image boxes.
        //    Pattern: <img ...> where src= is missing entirely, or src is empty.
        String result = html.replaceAll("<img\\s+[^>]*?src\\s*=\\s*[\"']\\s*[\"'][^>]*?>", "");
        result = result.replaceAll("<img\\s+[^>]*?src\\s*=\\s*[\"']\\s*[\"'][^>]*?/>", "");
        // Strip <img> tags that have no src= attribute at all (e.g. <img alt="" style="">)
        result = result.replaceAll("<img(?![^>]*?src=)[^>]*?/?>", "");

        // 2. Self-close void elements for Flying Saucer's XML parser
        result = result.replaceAll("<br(\\s*/?)?>", "<br/>");
        result = result.replaceAll("<hr(\\s*/?)?>", "<hr/>");
        result = result.replaceAll("<img([^>]*?)(?<!/)>", "<img$1/>");
        result = result.replaceAll("<input([^>]*?)(?<!/)>", "<input$1/>");

        // 2. Replace HTML-only entities with numeric XML equivalents.
        //    Flying Saucer uses an XML parser and will choke on &nbsp; &amp; etc.
        result = result.replace("&nbsp;", "&#160;");
        result = result.replace("&ensp;", "&#8194;");
        result = result.replace("&emsp;", "&#8195;");
        result = result.replace("&lt;", "&#60;");
        result = result.replace("&gt;", "&#62;");
        result = result.replace("&amp;", "&#38;");
        result = result.replace("&quot;", "&#34;");
        result = result.replace("&apos;", "&#39;");
        result = result.replace("&laquo;", "&#171;");
        result = result.replace("&raquo;", "&#187;");
        result = result.replace("&mdash;", "&#8212;");
        result = result.replace("&ndash;", "&#8211;");
        result = result.replace("&bull;", "&#8226;");
        result = result.replace("&hellip;", "&#8230;");
        result = result.replace("&copy;", "&#169;");
        result = result.replace("&reg;", "&#174;");
        result = result.replace("&trade;", "&#8482;");

        // 3. Escape any remaining bare & that is not part of a valid XML entity
        result = result.replaceAll("&(?!(?:#[0-9]+|#x[0-9a-fA-F]+);)", "&amp;");
        return result;
    }

    // Precomputed Kangxi Radical (U+2F00-U+2FDF) to standard CJK ideograph mapping.
    // Index position corresponds to the codepoint offset: index N → U+2F00+N.
    // Generated per the Unicode Standard Kangxi Radicals chart.
    private static final String KANGXI_TO_CJK = buildKangxiCjkString();
    private static String buildKangxiCjkString() {
        // @formatter:off
        // 214 radicals (U+2F00-U+2FD5) mapped to their standard CJK head character.
        // Listed in Kangxi dictionary radical order 1-214.
        int[] kc = new int[]{
            0x4E00,0x4E28,0x4E36,0x4E3F,0x4E59,0x4E85,0x4E8C,0x4EA0, // 2F00-2F07
            0x4EBA,0x513F,0x5165,0x516B,0x5182,0x5196,0x51AB,0x51E0, // 2F08-2F0F
            0x51F5,0x5200,0x529B,0x52F9,0x5315,0x531A,0x5338,0x5341, // 2F10-2F17
            0x535C,0x5369,0x5382,0x53B6,0x53C8,0x53E3,0x56D7,0x571F, // 2F18-2F1F
            0x58EB,0x5902,0x590A,0x5915,0x5927,0x5973,0x5B50,0x5B80, // 2F20-2F27
            0x5BF8,0x5C0F,0x5C22,0x5C38,0x5C6E,0x5C71,0x5DDB,0x5DE5, // 2F28-2F2F
            0x5DF2,0x5DFE,0x5E72,0x5E7A,0x5E7F,0x5EF4,0x5EFE,0x5F0B, // 2F30-2F37
            0x5F13,0x5F50,0x5F61,0x5F73,0x5FC3,0x6208,0x6236,0x624B, // 2F38-2F3F
            0x652F,0x6534,0x6587,0x6597,0x65A4,0x65B9,0x65E0,0x65E5, // 2F40-2F47
            0x66F0,0x6708,0x6728,0x6B20,0x6B62,0x6B79,0x6BB3,0x6BCB, // 2F48-2F4F
            0x6BD4,0x6BDB,0x6C0F,0x6C14,0x6C34,0x706B,0x722A,0x7236, // 2F50-2F57
            0x723B,0x723F,0x7247,0x7259,0x725B,0x72AC,0x7384,0x7389, // 2F58-2F5F
            0x74DC,0x74E6,0x7518,0x751F,0x7528,0x7530,0x758B,0x7592, // 2F60-2F67
            0x7676,0x767D,0x76AE,0x76BF,0x76EE,0x77DB,0x77E2,0x77F3, // 2F68-2F6F
            0x793A,0x79B8,0x79BE,0x7A74,0x7ACB,0x7AF9,0x7C73,0x7CF8, // 2F70-2F77
            0x7F36,0x7F51,0x7F8A,0x7FBD,0x8001,0x800C,0x8012,0x8033, // 2F78-2F7F
            0x807F,0x8089,0x81E3,0x81EA,0x81F3,0x81FC,0x820C,0x821B, // 2F80-2F87
            0x821F,0x826E,0x8272,0x8278,0x864D,0x866B,0x8840,0x884C, // 2F88-2F8F
            0x8863,0x897E,0x898B,0x89D2,0x8A00,0x8C37,0x8C46,0x8C55, // 2F90-2F97
            0x8C78,0x8C9D,0x8D64,0x8D70,0x8DB3,0x8EAB,0x8ECA,0x8F9B, // 2F98-2F9F
            0x8FB0,0x8FB5,0x9091,0x9149,0x91C6,0x91CC,0x91D1,0x9577, // 2FA0-2FA7
            0x9580,0x961C,0x96B6,0x96B9,0x96E8,0x9751,0x975E,0x9762, // 2FA8-2FAF
            0x9769,0x97CB,0x97ED,0x97F3,0x9801,0x98A8,0x98DB,0x98DF, // 2FB0-2FB7
            0x9996,0x9999,0x99AC,0x9AA8,0x9AD8,0x9ADF,0x9B25,0x9B2F, // 2FB8-2FBF
            0x9B32,0x9B3C,0x9B5A,0x9CE5,0x9E75,0x9E7F,0x9EA5,0x9EBB, // 2FC0-2FC7
            0x9EC3,0x9ECD,0x9ED1,0x9EF9,0x9EFD,0x9F0E,0x9F13,0x9F20, // 2FC8-2FCF
            0x9F3B,0x9F4A,0x9F52,0x9F8D,0x9F9C,0x9FA0,               // 2FD0-2FD5
            // Additional supplement radicals (U+2FD6-U+2FDF)
            0x2F00,0x2F01,0x2F02,0x2F03,0x2F04,0x2F05,0x2F06,0x2F07,
            0x2F08,0x2F09,
        }; // fallback for supplement: leave as-is or map to self
        // @formatter:on
        return new String(kc, 0, kc.length);
    }

    /**
     * Normalize special Unicode characters to their standard CJK equivalents
     * so they render correctly in PDFs using common CJK fonts.
     *
     * Handles:
     * - Kangxi Radicals (U+2F00-U+2FDF) → standard CJK ideographs
     * - Private Use Area characters (U+E000-U+F8FF) — decorative icons, removed
     */
    private String normalizeContent(String html) {
        if (html == null || html.isEmpty()) return html;

        // 1. Remove Private Use Area characters (decorative icons in resume templates)
        html = html.replaceAll("[\\uE000-\\uF8FF]", "");

        // 2. Map Kangxi Radicals (U+2F00-U+2FDF) to standard CJK ideographs.
        //    Resume templates frequently use radicals instead of the standard form,
        //    which most CJK fonts don't support (glyph would be invisible in PDF).
        int[] cps = html.codePoints().toArray();
        StringBuilder sb = new StringBuilder(cps.length);
        for (int cp : cps) {
            if (cp >= 0x2F00 && cp <= 0x2FDF) {
                int idx = cp - 0x2F00;
                if (idx < KANGXI_TO_CJK.length()) {
                    sb.append(KANGXI_TO_CJK.charAt(idx));
                }
            } else {
                sb.appendCodePoint(cp);
            }
        }
        return sb.toString();
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
            PDType0Font font = loadChineseFont(document);
            float fontSize = 11;
            float leading = 16f;
            // A4 portrait: 595 x 842 pt. Printable area with 50pt margins.
            float marginX = 50;
            float marginY = 50;
            float pageHeight = PDRectangle.A4.getHeight();
            float pageWidth = PDRectangle.A4.getWidth();
            float startY = pageHeight - marginY;
            float bottomMarginY = marginY;
            float maxLinesPerPage = (startY - bottomMarginY) / leading;
            // Rough CJK characters per line matching A4 printable width at 11pt
            int maxCharsPerLine = 50;

            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDPageContentStream cs = new PDPageContentStream(document, page);
            cs.setFont(font, fontSize);
            cs.setLeading(leading);
            cs.beginText();
            cs.newLineAtOffset(marginX, startY);

            int linesOnPage = 0;

            for (String line : plainText.split("\n")) {
                // Check if we need a page break before writing (blank lines count too)
                if (linesOnPage > 0 && linesOnPage >= maxLinesPerPage) {
                    cs.endText();
                    cs.close();

                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    cs = new PDPageContentStream(document, page);
                    cs.setFont(font, fontSize);
                    cs.setLeading(leading);
                    cs.beginText();
                    cs.newLineAtOffset(marginX, startY);
                    linesOnPage = 0;
                }

                if (line.trim().isEmpty()) {
                    cs.newLine();
                    linesOnPage++;
                    continue;
                }

                String text = line.trim();
                while (text.length() > maxCharsPerLine) {
                    // Page break before a wrapped sub-line if we're at the limit
                    if (linesOnPage >= maxLinesPerPage) {
                        cs.endText();
                        cs.close();

                        page = new PDPage(PDRectangle.A4);
                        document.addPage(page);
                        cs = new PDPageContentStream(document, page);
                        cs.setFont(font, fontSize);
                        cs.setLeading(leading);
                        cs.beginText();
                        cs.newLineAtOffset(marginX, startY);
                        linesOnPage = 0;
                    }

                    cs.showText(text.substring(0, maxCharsPerLine));
                    cs.newLine();
                    linesOnPage++;
                    text = text.substring(maxCharsPerLine);
                }

                // Page break before the remaining text if needed
                if (linesOnPage >= maxLinesPerPage) {
                    cs.endText();
                    cs.close();

                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    cs = new PDPageContentStream(document, page);
                    cs.setFont(font, fontSize);
                    cs.setLeading(leading);
                    cs.beginText();
                    cs.newLineAtOffset(marginX, startY);
                    linesOnPage = 0;
                }

                cs.showText(text);
                cs.newLine();
                linesOnPage++;
            }

            cs.endText();
            cs.close();

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
            fontsDir + "/simfang.ttf",
            fontsDir + "/simhei.ttf",
            fontsDir + "/simkai.ttf",
            fontsDir + "/simsunb.ttf",
        };
        for (String path : ttfCandidates) {
            File f = new File(path);
            if (f.exists()) {
                return PDType0Font.load(document, new java.io.FileInputStream(f), true);
            }
        }
        // Fallback: try TTC files with TrueTypeCollection
        String[] ttcCandidates = {
            fontsDir + "/msyh.ttc",
            fontsDir + "/simsun.ttc",
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
     * Detects multi-column layouts and returns text as vertical column blocks
     * separated by "---", with each column's content in top-to-bottom order.
     */
    public String extractText(byte[] fileData) {
        try (PDDocument document = Loader.loadPDF(fileData)) {
            PDFTextStripper stripper = new PDFTextStripper() {
                private final List<TextPosition> pageTexts = new ArrayList<>();

                @Override
                protected void processTextPosition(TextPosition text) {
                    pageTexts.add(text);
                }

                @Override
                protected void writePage() throws IOException {
                    if (!pageTexts.isEmpty()) {
                        String formatted = formatColumns(pageTexts);
                        if (log.isDebugEnabled()) {
                            log.debug("PDF formatted output:\n{}", formatted);
                        }
                        writeString(formatted);
                        pageTexts.clear();
                    }
                }
            };
            stripper.setSortByPosition(true);
            String result = stripper.getText(document).trim();
            return result;
        } catch (IOException e) {
            throw new BusinessException(400, "PDF文件解析失败: " + e.getMessage());
        }
    }

    /**
     * Given a list of TextPosition objects (in content-stream order),
     * detect column boundaries using line-level gap analysis and return text
     * formatted as vertical column blocks separated by "---".
     *
     * Column detection works by grouping text into visual lines (by Y),
     * then finding X-gaps between fragments on the same line that exceed
     * 8% of page width. Gap positions that appear on at least 2 different
     * lines are treated as column boundaries. This naturally excludes
     * centered titles/headings (single fragment per line) from column detection.
     */
    private String formatColumns(List<TextPosition> texts) {
        if (texts.isEmpty()) return "";

        float pageWidth = 612f;

        // 1. Group into visual lines by absolute Y coordinate.
        //    Using absolute Y (within tolerance) rather than stream order ensures
        //    fragments from different columns at the same vertical position
        //    are grouped into the same line for column boundary detection.
        List<List<TextPosition>> lines = new ArrayList<>();
        List<Float> lineYs = new ArrayList<>();
        for (TextPosition tp : texts) {
            float y = Math.round(tp.getY());
            int idx = -1;
            for (int i = 0; i < lineYs.size(); i++) {
                if (Math.abs(lineYs.get(i) - y) <= 3) {
                    idx = i;
                    break;
                }
            }
            if (idx >= 0) {
                lines.get(idx).add(tp);
            } else {
                lineYs.add(y);
                List<TextPosition> line = new ArrayList<>();
                line.add(tp);
                lines.add(line);
            }
        }
        // Sort lines top-to-bottom by Y
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < lineYs.size(); i++) order.add(i);
        order.sort((a, b) -> Float.compare(lineYs.get(a), lineYs.get(b)));
        List<List<TextPosition>> sortedLines = new ArrayList<>();
        for (int idx : order) sortedLines.add(lines.get(idx));
        lines = sortedLines;

        // Detect column boundaries from line-level X gaps
        List<Float> boundaries = new ArrayList<>();
        boundaries.add(-1f);
        List<Float> columnGaps = detectColumnBoundaries(lines, pageWidth);
        boundaries.addAll(columnGaps);
        boundaries.add(Float.MAX_VALUE);

        int numCols = boundaries.size() - 1;

        // 2. Process each Y-group (visual line) as a unit.
        //    Lines with an internal gap > 8% page width contain multiple columns
        //    (assign fragments to their respective column).
        //    Lines with no such gap (continuous text) that cross a column boundary
        //    are heading lines — output before column blocks.
        float minGap = pageWidth * 0.08f;
        List<StringBuilder> headingLines = new ArrayList<>();
        List<List<StringBuilder>> colLines = new ArrayList<>(numCols);
        for (int c = 0; c < numCols; c++) {
            colLines.add(new ArrayList<>());
            colLines.get(c).add(new StringBuilder());
        }

        for (List<TextPosition> line : lines) {
            // Sort fragments left-to-right by X
            line.sort(Comparator.comparing(TextPosition::getX));

            // Check if this line has an internal column gap (multiple columns on same line)
            boolean hasColumnGap = false;
            for (int i = 1; i < line.size(); i++) {
                float g = line.get(i).getX()
                    - (line.get(i - 1).getX() + line.get(i - 1).getWidth());
                if (g > minGap) {
                    hasColumnGap = true;
                    break;
                }
            }

            if (hasColumnGap) {
                // Column content line — assign fragments by X-center
                for (TextPosition tp : line) {
                    int col = columnIndex(tp.getX() + tp.getWidth() / 2, boundaries);
                    if (col >= 0 && col < numCols) {
                        colLines.get(col).get(colLines.get(col).size() - 1).append(tp.getUnicode());
                    }
                }
                // Prepare next line slot for each column
                for (int c = 0; c < numCols; c++) {
                    colLines.get(c).add(new StringBuilder());
                }
            } else if (numCols > 1) {
                // No internal gap — check if this continuous line spans a column boundary
                float minX = line.get(0).getX();
                float maxX = line.get(line.size() - 1).getX() + line.get(line.size() - 1).getWidth();
                boolean spansBoundary = false;
                for (float gap : columnGaps) {
                    if (minX < gap && maxX > gap) {
                        spansBoundary = true;
                        break;
                    }
                }
                if (spansBoundary) {
                    StringBuilder sb = new StringBuilder();
                    for (TextPosition tp : line) {
                        sb.append(tp.getUnicode());
                    }
                    headingLines.add(sb);
                } else {
                    // Continuous text within a single column
                    int assignedCol = -1;
                    for (TextPosition tp : line) {
                        int col = columnIndex(tp.getX() + tp.getWidth() / 2, boundaries);
                        if (col >= 0 && col < numCols) {
                            colLines.get(col).get(colLines.get(col).size() - 1).append(tp.getUnicode());
                            if (assignedCol == -1) assignedCol = col;
                        }
                    }
                    StringBuilder fullLine = new StringBuilder();
                    for (TextPosition tp : line) fullLine.append(tp.getUnicode());
                    log.debug("LINE Y={} -> col {}: '{}'", line.get(0).getY(), assignedCol, fullLine.toString().trim());
                    for (int c = 0; c < numCols; c++) {
                        colLines.get(c).add(new StringBuilder());
                    }
                }
            } else {
                // Single column — assign all to column 0
                for (TextPosition tp : line) {
                    colLines.get(0).get(colLines.get(0).size() - 1).append(tp.getUnicode());
                }
                for (int c = 0; c < numCols; c++) {
                    colLines.get(c).add(new StringBuilder());
                }
            }
        }

        // 3. Assemble output with heading lines first, then column blocks
        StringBuilder result = new StringBuilder();

        // Heading lines
        for (int i = 0; i < headingLines.size(); i++) {
            String h = headingLines.get(i).toString().trim();
            if (!h.isEmpty()) {
                if (result.length() > 0) result.append("\n");
                result.append(h);
            }
        }

        // Column content (skip empty trailing newline slot)
        for (int c = 0; c < numCols; c++) {
            List<StringBuilder> column = colLines.get(c);
            if (!column.isEmpty() && column.get(column.size() - 1).length() == 0) {
                column.remove(column.size() - 1);
            }
            boolean hasContent = column.stream().anyMatch(sb -> sb.toString().trim().length() > 0);
            if (!hasContent) continue;

            if (result.length() > 0) result.append("\n");
            if (c > 0) result.append("---\n\n");
            boolean first = true;
            for (StringBuilder sb : column) {
                String t = sb.toString().trim();
                if (!t.isEmpty()) {
                    if (!first) result.append("\n");
                    result.append(t);
                    first = false;
                }
            }
        }

        return result.toString().trim();
    }

    /**
     * Detect column boundaries from visual line X-coordinate gaps.
     * Uses gap voting (gaps ≥8% page width across ≥2 lines) and validates
     * that columns contain long-form content. Falls back to sidebar detection
     * for right-edge content clusters.
     */
    private List<Float> detectColumnBoundaries(List<List<TextPosition>> lines, float pageWidth) {
        float minGap = pageWidth * 0.08f;
        List<Float> columnGaps = new ArrayList<>();

        // Phase 1: Gap voting — collect X-gap positions from multi-fragment lines
        Map<Float, Integer> gapVotes = new HashMap<>();
        for (List<TextPosition> line : lines) {
            if (line.size() < 2) continue;
            line.sort((a, b) -> Float.compare(a.getX(), b.getX()));
            for (int i = 1; i < line.size(); i++) {
                float gap = line.get(i).getX()
                    - (line.get(i - 1).getX() + line.get(i - 1).getWidth());
                if (gap > minGap) {
                    float gapCenter = (line.get(i - 1).getX() + line.get(i - 1).getWidth()
                        + line.get(i).getX()) / 2;
                    boolean merged = false;
                    for (Map.Entry<Float, Integer> entry : gapVotes.entrySet()) {
                        if (Math.abs(entry.getKey() - gapCenter) <= 10) {
                            entry.setValue(entry.getValue() + 1);
                            merged = true;
                            break;
                        }
                    }
                    if (!merged) {
                        gapVotes.put(gapCenter, 1);
                    }
                }
            }
        }
        for (Map.Entry<Float, Integer> entry : gapVotes.entrySet()) {
            if (entry.getValue() >= 2) {
                columnGaps.add(entry.getKey());
            }
        }
        columnGaps.sort(Float::compare);

        // Validate gaps: require left content to extend past 25% page width
        List<Float> validatedGaps = new ArrayList<>();
        for (float gap : columnGaps) {
            float totalLeftEnd = 0;
            int gapLines = 0;
            for (List<TextPosition> line : lines) {
                if (line.size() < 2) continue;
                line.sort((a, b) -> Float.compare(a.getX(), b.getX()));
                for (int i = 1; i < line.size(); i++) {
                    float g = line.get(i).getX()
                        - (line.get(i - 1).getX() + line.get(i - 1).getWidth());
                    if (g > minGap) {
                        float gc = (line.get(i - 1).getX() + line.get(i - 1).getWidth()
                            + line.get(i).getX()) / 2;
                        if (Math.abs(gc - gap) <= 10) {
                            totalLeftEnd += line.get(i - 1).getX() + line.get(i - 1).getWidth();
                            gapLines++;
                            break;
                        }
                    }
                }
            }
            if (gapLines > 0 && (totalLeftEnd / gapLines) > pageWidth * 0.25f) {
                validatedGaps.add(gap);
            }
        }
        columnGaps = validatedGaps;

        // Phase 2: Sidebar detection for right-edge content clusters
        if (columnGaps.isEmpty()) {
            float sidebarThreshold = pageWidth * 0.72f;
            int sidebarLines = 0;
            int pairedSidebarLines = 0;
            float minSidebarX = pageWidth;
            for (List<TextPosition> line : lines) {
                boolean hasRightEdge = false;
                boolean hasLeft = false;
                for (TextPosition tp : line) {
                    if (tp.getX() >= sidebarThreshold) {
                        hasRightEdge = true;
                        minSidebarX = Math.min(minSidebarX, tp.getX());
                    } else {
                        hasLeft = true;
                    }
                }
                if (hasRightEdge) {
                    sidebarLines++;
                    if (hasLeft) pairedSidebarLines++;
                }
            }
            int unpairedSidebar = sidebarLines - pairedSidebarLines;
            if (sidebarLines >= 3 && unpairedSidebar >= 2) {
                float maxLeftX = 0;
                for (List<TextPosition> line : lines) {
                    for (TextPosition tp : line) {
                        float xc = tp.getX() + tp.getWidth() / 2;
                        if (xc < minSidebarX) {
                            maxLeftX = Math.max(maxLeftX, tp.getX() + tp.getWidth());
                        }
                    }
                }
                float b = (maxLeftX + minSidebarX) / 2f;
                log.debug("SIDEBAR: minX={} maxLeftX={} boundary={} lines={} unpaired={}",
                    minSidebarX, maxLeftX, b, sidebarLines, unpairedSidebar);
                columnGaps.add(b);
            }
        }

        return columnGaps;
    }

    private static int columnIndex(float xCenter, List<Float> boundaries) {
        for (int i = 0; i < boundaries.size() - 1; i++) {
            if (xCenter >= boundaries.get(i) && xCenter < boundaries.get(i + 1)) {
                return i;
            }
        }
        return 0;
    }

}
