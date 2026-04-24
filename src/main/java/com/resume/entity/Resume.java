package com.resume.entity;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "简历实体")
public class Resume {
    @Schema(description = "主键ID")
    private Long id;

    @Schema(description = "用户名", example = "张三")
    private String username;

    @Schema(description = "工作年限", example = "5")
    private Integer workYears;

    @Schema(description = "年龄", example = "28")
    private Integer age;

    @Schema(description = "简历内容")
    private String resumeContent;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Integer getWorkYears() { return workYears; }
    public void setWorkYears(Integer workYears) { this.workYears = workYears; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    public String getResumeContent() { return resumeContent; }
    public void setResumeContent(String resumeContent) { this.resumeContent = resumeContent; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
