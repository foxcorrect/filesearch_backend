package com.resume.controller;

import com.resume.config.LoginRateLimiter;
import com.resume.config.TokenBlacklist;
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

@Tag(name = "管理员认证", description = "管理员登录与登出接口")
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final LoginRateLimiter loginRateLimiter;
    private final TokenBlacklist tokenBlacklist;

    public AdminController(AdminService adminService, LoginRateLimiter loginRateLimiter,
                           TokenBlacklist tokenBlacklist) {
        this.adminService = adminService;
        this.loginRateLimiter = loginRateLimiter;
        this.tokenBlacklist = tokenBlacklist;
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

    @Operation(summary = "管理员登出", description = "将当前token加入黑名单以实现登出")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            tokenBlacklist.blacklist(authHeader.substring(7));
        }
        return ApiResponse.success();
    }
}
