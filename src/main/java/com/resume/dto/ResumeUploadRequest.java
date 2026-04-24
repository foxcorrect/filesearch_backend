package com.resume.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

@Schema(description = "PDF简历上传请求参数")
public class ResumeUploadRequest {

    @NotNull(message = "请选择要上传的文件")
    private MultipartFile file;

    @NotBlank(message = "姓名不能为空")
    private String username;

    @NotNull(message = "年龄不能为空")
    @Min(value = 0, message = "年龄不能小于0")
    @Max(value = 150, message = "年龄不能超过150")
    private Integer age;

    @NotBlank(message = "性别不能为空")
    private String gender;

    @NotNull(message = "工作年限不能为空")
    @Min(value = 0, message = "工作年限不能小于0")
    @Max(value = 70, message = "工作年限不能超过70")
    private Integer workYears;

    public MultipartFile getFile() { return file; }
    public void setFile(MultipartFile file) { this.file = file; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public Integer getWorkYears() { return workYears; }
    public void setWorkYears(Integer workYears) { this.workYears = workYears; }
}
