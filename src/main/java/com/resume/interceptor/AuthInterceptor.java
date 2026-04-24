package com.resume.interceptor;

import com.resume.config.TokenBlacklist;
import com.resume.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final String jwtSecret;
    private final ObjectMapper objectMapper;
    private final TokenBlacklist tokenBlacklist;

    public AuthInterceptor(@Value("${jwt.secret}") String jwtSecret,
                           ObjectMapper objectMapper,
                           TokenBlacklist tokenBlacklist) {
        this.jwtSecret = jwtSecret;
        this.objectMapper = objectMapper;
        this.tokenBlacklist = tokenBlacklist;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write(objectMapper.writeValueAsString(
                    ApiResponse.unauthorized("未登录或token无效")
            ));
            return false;
        }

        String token = authHeader.substring(7);

        if (tokenBlacklist.isBlacklisted(token)) {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write(objectMapper.writeValueAsString(
                    ApiResponse.unauthorized("token已过期或无效")
            ));
            return false;
        }

        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            request.setAttribute("username", claims.getSubject());
            request.setAttribute("token", token);
            return true;
        } catch (Exception e) {
            response.setContentType("application/json;charset=UTF-8");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write(objectMapper.writeValueAsString(
                    ApiResponse.unauthorized("token已过期或无效")
            ));
            return false;
        }
    }
}
