package com.resume.service;

import com.resume.dto.ResumeUpdateRequest;
import com.resume.dto.PageResult;
import com.resume.entity.Resume;
import org.springframework.web.multipart.MultipartFile;

public interface ResumeService {
    PageResult<Resume> findAll(int page, int size);
    Resume findById(Long id);
    Resume create(Resume resume);
    Resume update(Long id, ResumeUpdateRequest request);
    Resume uploadPdf(MultipartFile file, String username, Integer age, String gender, Integer workYears);
    void updatePdfFile(Long id, MultipartFile file);
    byte[] getPdfFileData(Long id);
    String getPdfContent(Long id);
    void delete(Long id);
}
