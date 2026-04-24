package com.resume.service;

import com.resume.dto.ResumeUpdateRequest;
import com.resume.entity.Resume;
import com.resume.mapper.ResumeMapper;
import com.resume.service.impl.ResumeServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResumeServiceImplTest {

    @Mock
    private ResumeMapper resumeMapper;

    @InjectMocks
    private ResumeServiceImpl resumeService;

    @Test
    void findAll_shouldReturnAllResumes() {
        List<Resume> expected = List.of(new Resume(), new Resume());
        when(resumeMapper.findAll()).thenReturn(expected);

        List<Resume> result = resumeService.findAll();

        assertEquals(2, result.size());
        verify(resumeMapper).findAll();
    }

    @Test
    void findById_shouldReturnResume_whenExists() {
        Resume resume = new Resume();
        resume.setId(1L);
        when(resumeMapper.findById(1L)).thenReturn(resume);

        Resume result = resumeService.findById(1L);

        assertEquals(1L, result.getId());
        verify(resumeMapper).findById(1L);
    }

    @Test
    void findById_shouldThrow_whenNotFound() {
        when(resumeMapper.findById(anyLong())).thenReturn(null);

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> resumeService.findById(999L));
        assertEquals("简历不存在", e.getMessage());
    }

    @Test
    void update_shouldSucceed_whenResumeExists() {
        Resume existing = new Resume();
        existing.setId(1L);
        existing.setUsername("old");
        existing.setWorkYears(1);
        existing.setAge(20);

        Resume updated = new Resume();
        updated.setId(1L);
        updated.setUsername("new_name");
        updated.setWorkYears(5);
        updated.setAge(30);
        updated.setResumeContent("new content");

        when(resumeMapper.findById(1L)).thenReturn(existing, updated);

        ResumeUpdateRequest request = new ResumeUpdateRequest();
        request.setUsername("new_name");
        request.setWorkYears(5);
        request.setAge(30);
        request.setResumeContent("new content");

        Resume result = resumeService.update(1L, request);

        assertEquals("new_name", result.getUsername());
        assertEquals(5, result.getWorkYears());
        assertEquals(30, result.getAge());
        assertEquals("new content", result.getResumeContent());
        verify(resumeMapper).update(any(Resume.class));
    }

    @Test
    void update_shouldThrow_whenResumeNotFound() {
        when(resumeMapper.findById(anyLong())).thenReturn(null);

        ResumeUpdateRequest request = new ResumeUpdateRequest();
        RuntimeException e = assertThrows(RuntimeException.class,
                () -> resumeService.update(999L, request));
        assertEquals("简历不存在", e.getMessage());
    }
}
