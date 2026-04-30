package com.resume.controller;

import com.resume.dto.ApiResponse;
import com.resume.dto.PageResult;
import com.resume.dto.ResumeUpdateRequest;
import com.resume.dto.ResumeUploadRequest;
import com.resume.entity.Resume;
import com.resume.service.ResumeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "简历管理", description = "简历的查看与编辑接口（需登录）")
@SecurityRequirement(name = "BearerAuth")
@RestController
@RequestMapping("/api/resumes")
@Validated
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @Operation(summary = "上传PDF简历", description = "上传PDF文件，后端通过pdf2htmlEX转换为保留原始排版的HTML，同时存储原始PDF")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Resume> upload(@Valid @ModelAttribute ResumeUploadRequest request) {
        String originalFilename = request.getFile().getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".pdf")) {
            return ApiResponse.error(400, "仅支持PDF格式的文件");
        }
        String contentType = request.getFile().getContentType();
        if (contentType == null || !contentType.equalsIgnoreCase("application/pdf")) {
            return ApiResponse.error(400, "仅支持PDF格式的文件");
        }
        return ApiResponse.success(resumeService.uploadPdf(
                request.getFile(), request.getUsername(), request.getAge(),
                request.getGender(), request.getWorkYears()));
    }

    @Operation(summary = "获取所有简历（分页）", description = "分页获取已上传的简历列表")
    @GetMapping
    public ApiResponse<PageResult<Resume>> list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(resumeService.findAll(page, size));
    }

    @Operation(summary = "查看简历详情", description = "根据ID获取单个简历的详细信息")
    @GetMapping("/{id}")
    public ApiResponse<Resume> detail(@PathVariable Long id) {
        return ApiResponse.success(resumeService.findById(id));
    }

    @Operation(summary = "编辑简历", description = "更新简历信息和HTML内容（resumeContent为富文本编辑器输出的HTML）")
    @PutMapping("/{id}")
    public ApiResponse<Resume> update(@PathVariable Long id,
                                      @Valid @RequestBody ResumeUpdateRequest request) {
        return ApiResponse.success(resumeService.update(id, request));
    }

    @Operation(summary = "获取简历HTML内容", description = "返回存储的HTML内容（上传时由pdf2htmlEX转换生成，或编辑后保存的版本），用于编辑器加载和HTML预览")
    @GetMapping("/{id}/pdf")
    public ApiResponse<String> getPdfContent(@PathVariable Long id) {
        return ApiResponse.success(resumeService.getPdfContent(id));
    }

    @Operation(summary = "删除简历", description = "根据ID删除简历")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        resumeService.delete(id);
        return ApiResponse.success();
    }
}
