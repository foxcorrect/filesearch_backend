package com.resume.controller;

import com.resume.config.LoginRateLimiter;
import com.resume.dto.ApiResponse;
import com.resume.dto.LoginRequest;
import com.resume.exception.RateLimitException;
import com.resume.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "管理员认证", description = "管理员登录接口")
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final LoginRateLimiter loginRateLimiter;

    public AdminController(AdminService adminService, LoginRateLimiter loginRateLimiter) {
        this.adminService = adminService;
        this.loginRateLimiter = loginRateLimiter;
    }

    @Operation(summary = "管理员登录", description = "使用用户名密码登录，返回JWT Token用于后续接口鉴权")
    @PostMapping("/login")
    public ApiResponse<Map<String, String>> login(@Valid @RequestBody LoginRequest request,
                                                   HttpServletRequest servletRequest) {
        String ip = servletRequest.getRemoteAddr();
        if (!loginRateLimiter.isAllowed(ip)) {
            throw new RateLimitException("登录尝试过于频繁，请稍后再试");
        }
        String token = adminService.login(request.getUsername(), request.getPassword());
        return ApiResponse.success(Map.of("token", token));
    }
}
