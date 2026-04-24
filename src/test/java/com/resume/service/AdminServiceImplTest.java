package com.resume.service;

import com.resume.entity.Admin;
import com.resume.mapper.AdminMapper;
import com.resume.service.impl.AdminServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceImplTest {

    @Mock
    private AdminMapper adminMapper;

    private BCryptPasswordEncoder passwordEncoder;
    private AdminServiceImpl adminService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        adminService = new AdminServiceImpl(adminMapper, passwordEncoder,
                "TestJwtSecretKeyForUnitTestingMustBeAtLeast256BitsLong!!", 86400000L);
    }

    @Test
    void login_shouldSucceed_whenCredentialsAreValid() {
        Admin admin = new Admin();
        admin.setUsername("admin");
        admin.setPassword(passwordEncoder.encode("admin123"));

        when(adminMapper.findByUsername("admin")).thenReturn(admin);

        String token = adminService.login("admin", "admin123");
        assertNotNull(token);
        assertTrue(token.split("\\.").length == 3);
    }

    @Test
    void login_shouldThrow_whenUsernameNotFound() {
        when(adminMapper.findByUsername(anyString())).thenReturn(null);

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> adminService.login("unknown", "pwd"));
        assertEquals("用户名或密码错误", e.getMessage());
    }

    @Test
    void login_shouldThrow_whenPasswordIsWrong() {
        Admin admin = new Admin();
        admin.setUsername("admin");
        admin.setPassword(passwordEncoder.encode("correct_password"));

        when(adminMapper.findByUsername("admin")).thenReturn(admin);

        RuntimeException e = assertThrows(RuntimeException.class,
                () -> adminService.login("admin", "wrong_password"));
        assertEquals("用户名或密码错误", e.getMessage());
    }
}
