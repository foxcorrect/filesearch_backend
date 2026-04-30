package com.resume.service.impl;

import com.resume.dto.PageResult;
import com.resume.dto.ResumeUpdateRequest;
import com.resume.entity.Resume;
import com.resume.exception.BusinessException;
import com.resume.mapper.ResumeMapper;
import com.resume.service.Pdf2HtmlService;
import com.resume.service.ResumeService;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ResumeServiceImpl implements ResumeService {

    private static final Safelist HTML_SAFELIST = Safelist.relaxed()
            .addAttributes(":all", "class", "style")
            .addProtocols("img", "src", "data")
            .addTags("style");

    private final ResumeMapper resumeMapper;
    private final Pdf2HtmlService pdf2HtmlService;

    public ResumeServiceImpl(ResumeMapper resumeMapper, Pdf2HtmlService pdf2HtmlService) {
        this.resumeMapper = resumeMapper;
        this.pdf2HtmlService = pdf2HtmlService;
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
    @Transactional(rollbackFor = Exception.class)
    public Resume update(Long id, ResumeUpdateRequest request) {
        Resume existing = resumeMapper.findByIdForUpdate(id);
        if (existing == null) {
            throw new BusinessException(400, "简历不存在");
        }

        String safeContent = sanitizeHtml(request.getResumeContent());

        existing.setUsername(request.getUsername());
        existing.setWorkYears(request.getWorkYears());
        existing.setAge(request.getAge());
        existing.setGender(request.getGender());
        existing.setResumeContent(safeContent);
        existing.setUpdatedAt(LocalDateTime.now());

        resumeMapper.update(existing);

        return existing;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Resume uploadPdf(MultipartFile file, String username, Integer age, String gender, Integer workYears) {
        if (file.isEmpty()) {
            throw new BusinessException(400, "上传文件不能为空");
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new BusinessException(400, "文件大小不能超过10MB");
        }

        byte[] fileData;
        try (InputStream is = file.getInputStream()) {
            fileData = is.readAllBytes();
        } catch (IOException e) {
            throw new BusinessException(500, "读取PDF文件失败: " + e.getMessage());
        }
        String html = pdf2HtmlService.convert(fileData);
        Resume resume = new Resume();
        resume.setUsername(username);
        resume.setAge(age);
        resume.setGender(gender);
        resume.setWorkYears(workYears);
        resume.setResumeContent(html);
        resume.setFileData(fileData);
        return create(resume);
    }

    @Override
    public String getPdfContent(Long id) {
        Resume resume = resumeMapper.findById(id);
        if (resume == null) {
            throw new BusinessException(400, "简历不存在");
        }
        if (resume.getResumeContent() != null && !resume.getResumeContent().isBlank()) {
            return resume.getResumeContent();
        }
        throw new BusinessException(400, "该简历暂无内容");
    }

    @Override
    public void delete(Long id) {
        Resume existing = resumeMapper.findById(id);
        if (existing == null) {
            throw new BusinessException(400, "简历不存在");
        }
        resumeMapper.deleteById(id);
    }

    private String sanitizeHtml(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        Document doc = Jsoup.parse(content);

        // Preserve <style> elements from <head> (contain CSS rules critical for PDF layout)
        Elements headStyles = doc.head().select("style");
        StringBuilder stylesHtml = new StringBuilder();
        for (Element style : headStyles) {
            stylesHtml.append(style.outerHtml());
        }

        // Only clean body content — head elements like <title> leak text when stripped by Safelist
        String bodyHtml = doc.body().html();

        return Jsoup.clean(stylesHtml.toString() + bodyHtml, HTML_SAFELIST);
    }
}
