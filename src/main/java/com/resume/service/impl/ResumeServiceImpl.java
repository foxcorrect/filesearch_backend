package com.resume.service.impl;

import com.resume.dto.ResumeUpdateRequest;
import com.resume.entity.Resume;
import com.resume.exception.BusinessException;
import com.resume.mapper.ResumeMapper;
import com.resume.service.ResumeService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ResumeServiceImpl implements ResumeService {

    private final ResumeMapper resumeMapper;

    public ResumeServiceImpl(ResumeMapper resumeMapper) {
        this.resumeMapper = resumeMapper;
    }

    @Override
    public List<Resume> findAll() {
        return resumeMapper.findAll();
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
    public Resume update(Long id, ResumeUpdateRequest request) {
        Resume existing = resumeMapper.findById(id);
        if (existing == null) {
            throw new BusinessException(400, "简历不存在");
        }

        existing.setUsername(request.getUsername());
        existing.setWorkYears(request.getWorkYears());
        existing.setAge(request.getAge());
        existing.setResumeContent(request.getResumeContent());
        existing.setUpdatedAt(LocalDateTime.now());

        resumeMapper.update(existing);
        return existing;
    }
}
