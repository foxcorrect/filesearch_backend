-- 创建数据库
CREATE DATABASE IF NOT EXISTS resume_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE resume_db;

-- 管理员表
CREATE TABLE IF NOT EXISTS admin (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员表';

-- 简历表
CREATE TABLE IF NOT EXISTS resume (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    work_years INT NOT NULL DEFAULT 0 COMMENT '工作年限',
    age INT NOT NULL DEFAULT 0 COMMENT '年龄',
    resume_content TEXT COMMENT '简历内容',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='简历表';

-- 插入测试简历数据
INSERT INTO resume (username, work_years, age, resume_content) VALUES
('张三', 5, 28, '5年后端开发经验，精通Java、Spring Boot、MyBatis，熟悉微服务架构设计，有高并发系统开发经验。'),
('李四', 8, 32, '8年全栈开发经验，擅长Java和Vue.js，具备大型项目管理能力，主导过多个电商平台建设。'),
('王五', 3, 26, '3年前端开发经验，熟练掌握React、Vue.js，对UI/UX有深刻理解。'),
('赵六', 10, 35, '10年运维开发经验，精通Docker、Kubernetes，有丰富的CI/CD流水线搭建经验。');
