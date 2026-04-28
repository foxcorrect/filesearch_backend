package com.resume.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ImageExtractionService {

    private static final Logger log = LoggerFactory.getLogger(ImageExtractionService.class);

    private static final Pattern IMG_DATA_URI_PATTERN = Pattern.compile(
        "<img\\s+([^>]*?)src\\s*=\\s*\"(data:image/[^;]+;base64,[^\"]+)\"([^>]*?)>",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern IMG_BLOCK_PATTERN = Pattern.compile(
        "===IMG_BLOCK_(\\d+)===\\s*\\n?(data:image/[^;]+;base64,[^\\n]+)");

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile(
        "\\[IMG_(\\d+)\\]");

    /**
     * Extract base64 data URI images from HTML content and append them as
     * independent blocks at the end of the content.
     *
     * @param htmlContent the original HTML with inline base64 images
     * @return processed content with [IMG_N] placeholders and image blocks appended
     */
    public String extractImages(String htmlContent) {
        if (htmlContent == null || htmlContent.isEmpty()) {
            return htmlContent;
        }

        // Check if already processed (contains image blocks)
        if (htmlContent.contains("===IMG_BLOCK_")) {
            return htmlContent;
        }

        Matcher m = IMG_DATA_URI_PATTERN.matcher(htmlContent);
        if (!m.find()) {
            return htmlContent;
        }

        // Collect all data URI images
        List<String> dataUris = new ArrayList<>();
        m.reset();
        StringBuffer sb = new StringBuffer(htmlContent.length());
        int index = 0;

        while (m.find()) {
            String dataUri = m.group(2);
            dataUris.add(dataUri);
            m.appendReplacement(sb, Matcher.quoteReplacement("[IMG_" + index + "]"));
            index++;
        }
        m.appendTail(sb);

        // Append image blocks
        StringBuilder result = new StringBuilder(sb.length() + dataUris.size() * 256);
        result.append(sb.toString().stripTrailing());
        result.append("\n\n");

        for (int i = 0; i < dataUris.size(); i++) {
            if (i > 0) result.append("\n");
            result.append("===IMG_BLOCK_").append(i).append("===\n");
            result.append(dataUris.get(i));
        }

        log.debug("Extracted {} images from HTML content", dataUris.size());
        return result.toString();
    }

    /**
     * Restore images from appended blocks back into HTML content.
     * Replaces [IMG_N] placeholders with the original &lt;img&gt; tags.
     *
     * @param processedContent content with [IMG_N] placeholders and image blocks
     * @return original HTML with inline data URI images, or unchanged if no blocks found
     */
    public String restoreImages(String processedContent) {
        if (processedContent == null || processedContent.isEmpty()) {
            return processedContent;
        }

        if (!processedContent.contains("===IMG_BLOCK_")) {
            return processedContent;
        }

        // Parse image blocks into a map: index -> data URI
        Map<Integer, String> imageBlocks = new LinkedHashMap<>();
        Matcher blockMatcher = IMG_BLOCK_PATTERN.matcher(processedContent);
        while (blockMatcher.find()) {
            int idx = Integer.parseInt(blockMatcher.group(1));
            String dataUri = blockMatcher.group(2);
            imageBlocks.put(idx, dataUri);
        }

        if (imageBlocks.isEmpty()) {
            return processedContent;
        }

        // Remove the image blocks section from content
        int blockSectionStart = processedContent.indexOf("===IMG_BLOCK_0===");
        String htmlBody;
        if (blockSectionStart > 0) {
            htmlBody = processedContent.substring(0, blockSectionStart).stripTrailing();
        } else {
            htmlBody = processedContent;
        }

        // Replace placeholders with img tags
        Matcher placeholderMatcher = PLACEHOLDER_PATTERN.matcher(htmlBody);
        StringBuffer sb = new StringBuffer(htmlBody.length());
        while (placeholderMatcher.find()) {
            int idx = Integer.parseInt(placeholderMatcher.group(1));
            String dataUri = imageBlocks.get(idx);
            if (dataUri != null) {
                placeholderMatcher.appendReplacement(sb,
                    Matcher.quoteReplacement("<img src=\"" + dataUri + "\">"));
            } else {
                placeholderMatcher.appendReplacement(sb, "$0");
            }
        }
        placeholderMatcher.appendTail(sb);

        return sb.toString();
    }
}
