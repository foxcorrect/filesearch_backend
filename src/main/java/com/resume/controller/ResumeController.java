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
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "简历管理", description = "简历的查看与编辑接口（需登录）")
@SecurityRequirement(name = "BearerAuth")
@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @Operation(summary = "上传PDF简历", description = "上传PDF格式的简历文件，同时传入姓名、年龄、性别、工作年限")
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
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 1) page = 1;
        if (size < 1) size = 20;
        if (size > 100) size = 100;
        return ApiResponse.success(resumeService.findAll(page, size));
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

    @Operation(summary = "获取PDF文本内容", description = "根据ID从原始PDF中提取文本内容，用于富文本编辑器")
    @GetMapping("/{id}/pdf")
    public ApiResponse<String> getPdfContent(@PathVariable Long id) {
        return ApiResponse.success(resumeService.getPdfContent(id));
    }

    @Operation(summary = "下载原始PDF", description = "根据ID下载原始PDF文件")
    @GetMapping("/{id}/pdf/download")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        byte[] fileData = resumeService.getPdfFileData(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.inline()
                .filename("resume.pdf")
                .build());
        return ResponseEntity.ok().headers(headers).body(fileData);
    }

    @Operation(summary = "更新PDF文件", description = "替换指定简历的原始PDF文件，同时重新提取文本内容")
    @PutMapping(value = "/{id}/pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Void> updatePdf(@PathVariable Long id,
                                        @RequestParam("file") MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(".pdf")) {
            return ApiResponse.error(400, "仅支持PDF格式的文件");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equalsIgnoreCase("application/pdf")) {
            return ApiResponse.error(400, "仅支持PDF格式的文件");
        }
        resumeService.updatePdfFile(id, file);
        return ApiResponse.success();
    }

    @Operation(summary = "删除简历", description = "根据ID删除简历")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        resumeService.delete(id);
        return ApiResponse.success();
    }
}
