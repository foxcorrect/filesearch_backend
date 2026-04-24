package com.resume.controller;

import com.resume.dto.ApiResponse;
import com.resume.dto.ResumeUpdateRequest;
import com.resume.entity.Resume;
import com.resume.service.ResumeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "简历管理", description = "简历的查看与编辑接口（需登录）")
@SecurityRequirement(name = "BearerAuth")
@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @Operation(summary = "获取所有简历", description = "获取所有已上传的简历列表")
    @GetMapping
    public ApiResponse<List<Resume>> list() {
        return ApiResponse.success(resumeService.findAll());
    }

    @Operation(summary = "查看简历详情", description = "根据ID获取单个简历的详细信息")
    @GetMapping("/{id}")
    public ApiResponse<Resume> detail(@PathVariable Long id) {
        return ApiResponse.success(resumeService.findById(id));
    }

    @Operation(summary = "编辑简历", description = "根据ID更新简历信息（用户名、工作年限、年龄、简历内容）")
    @PutMapping("/{id}")
    public ApiResponse<Resume> update(@PathVariable Long id,
                                      @Valid @RequestBody ResumeUpdateRequest request) {
        return ApiResponse.success(resumeService.update(id, request));
    }
}
