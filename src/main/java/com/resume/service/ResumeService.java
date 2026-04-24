package com.resume.service;

import com.resume.dto.ResumeUpdateRequest;
import com.resume.entity.Resume;

import java.util.List;

public interface ResumeService {
    List<Resume> findAll();
    Resume findById(Long id);
    Resume update(Long id, ResumeUpdateRequest request);
}
