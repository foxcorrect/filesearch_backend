package com.resume.service.impl;

import com.resume.dto.PageResult;
import com.resume.dto.ResumeUpdateRequest;
import com.resume.entity.Resume;
import com.resume.exception.BusinessException;
import com.resume.mapper.ResumeMapper;
import com.resume.service.PdfExtractionService;
import com.resume.service.ResumeService;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ResumeServiceImpl implements ResumeService {

    private static final Safelist HTML_SAFELIST = Safelist.relaxed()
            .addAttributes(":all", "class", "style")
            .addProtocols("img", "src", "data");

    private final ResumeMapper resumeMapper;
    private final PdfExtractionService pdfExtractionService;

    public ResumeServiceImpl(ResumeMapper resumeMapper, PdfExtractionService pdfExtractionService) {
        this.resumeMapper = resumeMapper;
        this.pdfExtractionService = pdfExtractionService;
    }

    @Override
    public PageResult<Resume> findAll(int page, int size) {
        int offset = (page - 1) * size;
        List<Resume> items = resumeMapper.findAll(offset, size);
        long total = resumeMapper.countAll();
        return new PageResult<>(items, total, page, size);
    }

    @Override
    public Resume findById(Long id) {
        Resume resume = resumeMapper.findById(id);
        if (resume == null) {
            throw new BusinessException(400, "简历不存在");
        }
        return resume;
    }

    @Override
    public Resume create(Resume resume) {
        LocalDateTime now = LocalDateTime.now();
        resume.setCreatedAt(now);
        resume.setUpdatedAt(now);
        resumeMapper.insert(resume);
        return resume;
    }

    @Override
    public Resume update(Long id, ResumeUpdateRequest request) {
        Resume existing = resumeMapper.findById(id);
        if (existing == null) {
            throw new BusinessException(400, "简历不存在");
        }

        // Sanitize HTML to prevent stored XSS
        String safeContent = sanitizeHtml(request.getResumeContent());

        existing.setUsername(request.getUsername());
        existing.setWorkYears(request.getWorkYears());
        existing.setAge(request.getAge());
        existing.setGender(request.getGender());
        existing.setResumeContent(safeContent);
        existing.setUpdatedAt(LocalDateTime.now());

        resumeMapper.update(existing);

        // Regenerate PDF from the updated editor content
        if (safeContent != null && !safeContent.isBlank()) {
            byte[] pdfData = pdfExtractionService.generateStyledPdf(safeContent);
            resumeMapper.updateFileData(id, pdfData);
        }

        return existing;
    }

    @Override
    public Resume uploadPdf(MultipartFile file, String username, Integer age, String gender, Integer workYears) {
        byte[] fileData;
        try {
            fileData = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(400, "读取PDF文件失败: " + e.getMessage());
        }
        String text = pdfExtractionService.extractText(fileData);
        Resume resume = new Resume();
        resume.setUsername(username);
        resume.setAge(age);
        resume.setGender(gender);
        resume.setWorkYears(workYears);
        resume.setResumeContent(text);
        resume.setFileData(fileData);
        return create(resume);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePdfFile(Long id, MultipartFile file) {
        Resume existing = resumeMapper.findById(id);
        if (existing == null) {
            throw new BusinessException(400, "简历不存在");
        }
        byte[] fileData;
        try {
            fileData = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(400, "读取PDF文件失败: " + e.getMessage());
        }
        String text = pdfExtractionService.extractText(fileData);
        existing.setResumeContent(text);
        existing.setUpdatedAt(LocalDateTime.now());
        // Update text content first, then file_data separately
        resumeMapper.update(existing);
        resumeMapper.updateFileData(id, fileData);
    }

    /**
     * Sanitize HTML content to remove XSS vectors (script tags, event handlers, etc.)
     * while preserving rich text editor formatting tags, class, and style attributes.
     */
    private String sanitizeHtml(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        return Jsoup.clean(content, HTML_SAFELIST);
    }

    @Override
    public byte[] getPdfFileData(Long id) {
        Resume resume = resumeMapper.findFileDataById(id);
        if (resume == null) {
            throw new BusinessException(400, "简历不存在");
        }
        if (resume.getFileData() == null) {
            throw new BusinessException(400, "该简历没有上传PDF文件");
        }
        return resume.getFileData();
    }

    @Override
    public String getPdfContent(Long id) {
        Resume resume = resumeMapper.findById(id);
        if (resume == null) {
            throw new BusinessException(400, "简历不存在");
        }
        // Return the stored content (edited text/HTML) if available,
        // otherwise fall back to extracting text from the original PDF
        if (resume.getResumeContent() != null && !resume.getResumeContent().isBlank()) {
            return resume.getResumeContent();
        }
        byte[] fileData = getPdfFileData(id);
        return pdfExtractionService.extractText(fileData);
    }

    @Override
    public void delete(Long id) {
        Resume existing = resumeMapper.findById(id);
        if (existing == null) {
            throw new BusinessException(400, "简历不存在");
        }
        resumeMapper.deleteById(id);
    }
}
