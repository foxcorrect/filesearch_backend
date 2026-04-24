package com.resume.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resume.config.TokenBlacklist;
import com.resume.config.WebMvcConfig;
import com.resume.dto.PageResult;
import com.resume.dto.ResumeUpdateRequest;
import com.resume.entity.Resume;
import com.resume.interceptor.AuthInterceptor;
import com.resume.service.ResumeService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ResumeController.class)
@Import({AuthInterceptor.class, WebMvcConfig.class, TokenBlacklist.class})
class ResumeControllerTest {

    private static final String JWT_SECRET = "TestJwtSecretKeyForUnitTestingMustBeAtLeast256BitsLong!!";
    private static String validToken;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ResumeService resumeService;

    @BeforeAll
    static void setupToken() {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        validToken = Jwts.builder()
                .subject("admin")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(key)
                .compact();
    }

    @Test
    void list_shouldReturnPaginatedResumes() throws Exception {
        Resume r1 = new Resume();
        r1.setId(1L);
        r1.setUsername("张三");
        r1.setWorkYears(5);
        r1.setAge(28);

        Resume r2 = new Resume();
        r2.setId(2L);
        r2.setUsername("李四");
        r2.setWorkYears(8);
        r2.setAge(32);

        PageResult<Resume> pageResult = new PageResult<>(List.of(r1, r2), 2, 1, 20);
        when(resumeService.findAll(anyInt(), anyInt())).thenReturn(pageResult);

        mockMvc.perform(get("/api/resumes")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.items[0].username").value("张三"))
                .andExpect(jsonPath("$.data.items[1].username").value("李四"));
    }

    @Test
    void detail_shouldReturnResume_whenExists() throws Exception {
        Resume resume = new Resume();
        resume.setId(1L);
        resume.setUsername("张三");
        resume.setWorkYears(5);
        resume.setAge(28);
        resume.setResumeContent("资深Java开发");
        resume.setCreatedAt(LocalDateTime.of(2024, 1, 1, 0, 0));
        resume.setUpdatedAt(LocalDateTime.of(2024, 1, 1, 0, 0));

        when(resumeService.findById(1L)).thenReturn(resume);

        mockMvc.perform(get("/api/resumes/1")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("张三"))
                .andExpect(jsonPath("$.data.workYears").value(5))
                .andExpect(jsonPath("$.data.resumeContent").value("资深Java开发"));
    }

    @Test
    void detail_shouldReturn400_whenNotFound() throws Exception {
        when(resumeService.findById(999L)).thenThrow(new com.resume.exception.BusinessException(400, "简历不存在"));

        mockMvc.perform(get("/api/resumes/999")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("简历不存在"));
    }

    @Test
    void update_shouldSucceed() throws Exception {
        Resume updated = new Resume();
        updated.setId(1L);
        updated.setUsername("张三");
        updated.setWorkYears(6);
        updated.setAge(29);
        updated.setResumeContent("更新后的简历内容");

        when(resumeService.update(eq(1L), any(ResumeUpdateRequest.class))).thenReturn(updated);

        ResumeUpdateRequest request = new ResumeUpdateRequest();
        request.setUsername("张三");
        request.setWorkYears(6);
        request.setAge(29);
        request.setResumeContent("更新后的简历内容");

        mockMvc.perform(put("/api/resumes/1")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.workYears").value(6))
                .andExpect(jsonPath("$.data.resumeContent").value("更新后的简历内容"));
    }

    @Test
    void update_shouldReturn400_whenValidationFails() throws Exception {
        ResumeUpdateRequest request = new ResumeUpdateRequest();
        request.setUsername("");
        request.setWorkYears(null);
        request.setAge(null);

        mockMvc.perform(put("/api/resumes/1")
                        .header("Authorization", "Bearer " + validToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void delete_shouldSucceed() throws Exception {
        mockMvc.perform(delete("/api/resumes/1")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void delete_shouldReturn400_whenNotFound() throws Exception {
        doThrow(new com.resume.exception.BusinessException(400, "简历不存在"))
                .when(resumeService).delete(999L);

        mockMvc.perform(delete("/api/resumes/999")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("简历不存在"));
    }

    @Test
    void shouldReturn401_whenNoToken() throws Exception {
        mockMvc.perform(get("/api/resumes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn401_whenInvalidToken() throws Exception {
        mockMvc.perform(get("/api/resumes")
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isUnauthorized());
    }
}
