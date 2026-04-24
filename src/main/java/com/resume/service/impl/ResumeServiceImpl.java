package com.resume.service.impl;

import com.resume.dto.PageResult;
import com.resume.dto.ResumeUpdateRequest;
import com.resume.entity.Resume;
import com.resume.exception.BusinessException;
import com.resume.mapper.ResumeMapper;
import com.resume.service.PdfExtractionService;
import com.resume.service.ResumeService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ResumeServiceImpl implements ResumeService {

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

        existing.setUsername(request.getUsername());
        existing.setWorkYears(request.getWorkYears());
        existing.setAge(request.getAge());
        existing.setGender(request.getGender());
        existing.setResumeContent(request.getResumeContent());
        existing.setUpdatedAt(LocalDateTime.now());

        resumeMapper.update(existing);
        return existing;
    }

    @Override
    public Resume uploadPdf(MultipartFile file, String username, Integer age, String gender, Integer workYears) {
        String text = pdfExtractionService.extractText(file);
        Resume resume = new Resume();
        resume.setUsername(username);
        resume.setAge(age);
        resume.setGender(gender);
        resume.setWorkYears(workYears);
        resume.setResumeContent(text);
        return create(resume);
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
