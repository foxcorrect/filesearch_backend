package com.resume.config;

import com.resume.entity.Admin;
import com.resume.mapper.AdminMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final AdminMapper adminMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final String defaultPassword;
    private final String jwtSecret;

    public DataInitializer(AdminMapper adminMapper,
                           BCryptPasswordEncoder passwordEncoder,
                           @Value("${admin.default-password:admin123}") String defaultPassword,
                           @Value("${jwt.secret}") String jwtSecret) {
        this.adminMapper = adminMapper;
        this.passwordEncoder = passwordEncoder;
        this.defaultPassword = defaultPassword;
        this.jwtSecret = jwtSecret;
    }

    @Override
    public void run(String... args) {
        // Security check: warn if default JWT secret is still in use
        if ("ResumeBackendJwtSecretKey2024MustBeAtLeast256BitsLongForHS256!!".equals(jwtSecret)) {
            log.warn("============================================");
            log.warn("!! 安全警告: 正在使用默认 JWT 密钥 !!");
            log.warn("!! 生产环境请设置环境变量 JWT_SECRET    !!");
            log.warn("============================================");
        }

        if (adminMapper.findByUsername("admin") == null) {
            Admin admin = new Admin();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode(defaultPassword));
            adminMapper.insert(admin);
            log.info("默认管理员账户已创建: admin（首次启动，请尽快通过管理界面修改密码）");
        }
    }
}
