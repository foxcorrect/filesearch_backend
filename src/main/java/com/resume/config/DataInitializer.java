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

    public DataInitializer(AdminMapper adminMapper,
                           BCryptPasswordEncoder passwordEncoder,
                           @Value("${admin.default-password:admin123}") String defaultPassword) {
        this.adminMapper = adminMapper;
        this.passwordEncoder = passwordEncoder;
        this.defaultPassword = defaultPassword;
    }

    @Override
    public void run(String... args) {
        if (adminMapper.findByUsername("admin") == null) {
            Admin admin = new Admin();
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode(defaultPassword));
            adminMapper.insert(admin);
            log.info("默认管理员已创建: admin / {}", defaultPassword);
        }
    }
}
