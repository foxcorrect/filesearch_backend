package com.resume.service;

import com.resume.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class Pdf2HtmlService {

    private static final Logger log = LoggerFactory.getLogger(Pdf2HtmlService.class);

    private static final String CONTAINER_NAME = "pdf2htmlex";
    private static final String CONTAINER_WORK_DIR = "/work";
    private static final long TIMEOUT_SECONDS = 30;

    private final String hostWorkDir;

    public Pdf2HtmlService(@Value("${pdf.work-dir:/tmp/resume-pdf}") String hostWorkDir) {
        this.hostWorkDir = hostWorkDir;
    }

    /**
     * Convert a PDF byte array to HTML using pdf2htmlEX running in a Docker container.
     * Uses a mounted volume ({@code E:\code\filesearch\pdf → /work}) so files are
     * directly accessible from both host and container without docker cp.
     *
     * @param pdfData raw PDF bytes
     * @return self-contained HTML string preserving original PDF layout
     * @throws BusinessException if conversion fails
     */
    public String convert(byte[] pdfData) {
        String jobId = UUID.randomUUID().toString().substring(0, 8);
        String inputName = "input_" + jobId + ".pdf";
        String outputName = "output_" + jobId + ".html";

        Path hostInput = Path.of(hostWorkDir, inputName);
        Path hostOutput = Path.of(hostWorkDir, outputName);

        try {
            // Ensure work directory exists
            Files.createDirectories(hostInput.getParent());

            // Write PDF to the mounted host directory (visible to container as /work/)
            Files.write(hostInput, pdfData);

            // Use pdftohtml (poppler) instead of pdf2htmlEX because it generates
            // static HTML without JavaScript. This means edits made via contentEditable
            // persist across saves — no JS renderer will overwrite DOM changes on reload.
            // -c: complex mode (preserves more layout), -s: single file, -dataurls: embed images
            runDockerCommand("exec", CONTAINER_NAME, "pdftohtml",
                    "-c", "-s", "-dataurls", "-noframes",
                    CONTAINER_WORK_DIR + "/" + inputName,
                    CONTAINER_WORK_DIR + "/" + outputName);

            // pdftohtml may append "-html.html" to the output filename
            Path actualOutput = hostOutput;
            Path altOutput = Path.of(hostWorkDir, outputName.replace(".html", "") + "-html.html");
            if (!Files.exists(actualOutput) && Files.exists(altOutput)) {
                actualOutput = altOutput;
            }

            String html = Files.readString(actualOutput, StandardCharsets.UTF_8);

            // Inline any remaining external references as a safety net
            html = inlineExternalResources(html, Path.of(hostWorkDir));

            log.debug("pdftohtml conversion successful, jobId={}, HTML size: {} bytes", jobId, html.length());
            return html;
        } catch (IOException e) {
            throw new BusinessException(500, "PDF转换失败: " + e.getMessage());
        } finally {
            deleteFile(hostInput);
            deleteFile(hostOutput);
            // pdftohtml may generate an XML file alongside the HTML
            deleteFile(Path.of(hostWorkDir, outputName.replace(".html", "") + "-html.xml"));
            deleteFile(Path.of(hostWorkDir, outputName.replace(".html", "") + ".xml"));
            deleteFile(Path.of(hostWorkDir, inputName + ".outline"));
        }
    }

    private void runDockerCommand(String... args) {
        try {
            String[] cmd = new String[args.length + 1];
            cmd[0] = "docker";
            System.arraycopy(args, 0, cmd, 1, args.length);

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new BusinessException(500, "PDF转换超时，请稍后重试");
            }

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                String errorOutput = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                throw new BusinessException(500, "PDF转换失败 (exit=" + exitCode + "): " + errorOutput);
            }
        } catch (IOException e) {
            throw new BusinessException(500, "Docker容器不可用: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(500, "PDF转换被中断");
        }
    }

    private static final Pattern SCRIPT_SRC = Pattern.compile(
        "<script\\s+src=\"([^\"]+)\"\\s*></script>", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINK_CSS = Pattern.compile(
        "<link\\s+rel=\"stylesheet\"\\s+href=\"([^\"]+)\"\\s*/?>", Pattern.CASE_INSENSITIVE);

    /**
     * Inline external JS and CSS files referenced in the HTML so the output is a single
     * self-contained file that works with iframe srcdoc (which cannot resolve relative URLs).
     */
    private String inlineExternalResources(String html, Path workDir) {
        html = inlineReferencedFiles(html, SCRIPT_SRC, workDir, "script");
        html = inlineReferencedFiles(html, LINK_CSS, workDir, "style");
        return html;
    }

    private String inlineReferencedFiles(String html, Pattern pattern, Path workDir, String wrapperTag) {
        Matcher m = pattern.matcher(html);
        StringBuilder sb = new StringBuilder(html.length() + 65536);
        Path normalizedWorkDir = workDir.toAbsolutePath().normalize();
        while (m.find()) {
            String src = m.group(1);
            if (src.startsWith("http://") || src.startsWith("https://")) {
                continue;
            }
            // Normalize the resolved path and verify it stays within workDir
            Path file = workDir.resolve(src).toAbsolutePath().normalize();
            if (!file.startsWith(normalizedWorkDir)) {
                log.warn("Blocked path traversal attempt: {}", src);
                continue;
            }
            try {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                m.appendReplacement(sb,
                    Matcher.quoteReplacement("<" + wrapperTag + ">" + content + "</" + wrapperTag + ">"));
            } catch (IOException e) {
                log.warn("Cannot inline {} file: {}", wrapperTag, src);
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private void deleteFile(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
            }
        }
    }
}
