package com.resume.service;

import com.resume.dto.PageResult;
import com.resume.dto.ResumeUpdateRequest;
import com.resume.entity.Resume;
import com.resume.exception.BusinessException;
import com.resume.mapper.ResumeMapper;
import com.resume.service.impl.ResumeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ResumeServiceImplTest {

    @Mock
    private ResumeMapper resumeMapper;

    @Mock
    private Pdf2HtmlService pdf2HtmlService;

    private ResumeServiceImpl resumeService;

    @BeforeEach
    void setUp() {
        resumeService = new ResumeServiceImpl(resumeMapper, pdf2HtmlService);
    }

    @Test
    void findAll_shouldReturnPaginatedResumes() {
        List<Resume> expected = List.of(new Resume(), new Resume());
        when(resumeMapper.findAll(anyInt(), anyInt())).thenReturn(expected);
        when(resumeMapper.countAll()).thenReturn(10L);

        PageResult<Resume> result = resumeService.findAll(1, 20);
        assertEquals(2, result.getItems().size());
        assertEquals(10, result.getTotal());
        assertEquals(1, result.getPage());
        assertEquals(20, result.getSize());
        verify(resumeMapper).findAll(0, 20);
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

        BusinessException e = assertThrows(BusinessException.class,
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

        when(resumeMapper.findById(1L)).thenReturn(existing);

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
        BusinessException e = assertThrows(BusinessException.class,
                () -> resumeService.update(999L, request));
        assertEquals("简历不存在", e.getMessage());
    }

    @Test
    void delete_shouldSucceed_whenResumeExists() {
        Resume existing = new Resume();
        existing.setId(1L);
        when(resumeMapper.findById(1L)).thenReturn(existing);

        resumeService.delete(1L);

        verify(resumeMapper).deleteById(1L);
    }

    @Test
    void delete_shouldThrow_whenResumeNotFound() {
        when(resumeMapper.findById(anyLong())).thenReturn(null);

        BusinessException e = assertThrows(BusinessException.class,
                () -> resumeService.delete(999L));
        assertEquals("简历不存在", e.getMessage());
    }
}
