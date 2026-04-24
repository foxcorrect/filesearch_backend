package com.resume.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "简历更新请求参数")
public class ResumeUpdateRequest {

    @Schema(description = "用户名", example = "张三")
    @NotBlank(message = "用户名不能为空")
    private String username;

    @Schema(description = "工作年限", example = "5")
    @NotNull(message = "工作年限不能为空")
    private Integer workYears;

    @Schema(description = "年龄", example = "28")
    @NotNull(message = "年龄不能为空")
    private Integer age;

    @Schema(description = "性别", example = "男")
    private String gender;

    @Schema(description = "简历内容", example = "5年后端开发经验，精通Java、Spring Boot、MyBatis。")
    @Size(max = 65535, message = "简历内容不能超过65535个字符")
    private String resumeContent;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Integer getWorkYears() { return workYears; }
    public void setWorkYears(Integer workYears) { this.workYears = workYears; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getResumeContent() { return resumeContent; }
    public void setResumeContent(String resumeContent) { this.resumeContent = resumeContent; }
}
