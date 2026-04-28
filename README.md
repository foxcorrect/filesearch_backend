# 简历管理系统 - 后端

基于 Spring Boot 的简历管理后端，支持 PDF 上传自动转换为 HTML、富文本在线编辑、分栏内容提取。

## 技术栈

- **Java 17**, Spring Boot 3.2.5
- **MyBatis 3.0.3** + MySQL
- **JWT** (HMAC-SHA256) 认证
- **Docker + pdftohtml** PDF 转 HTML
- **Jsoup** HTML 清洗
- **Flying Saucer** HTML 转 PDF
- **PDFBox** PDF 文本提取
- **SpringDoc OpenAPI** (Swagger UI)

## 项目结构

```
src/main/java/com/resume/
├── config/              # Spring 配置、异常处理、限流、JWT 黑名单
├── controller/          # REST 控制器
├── dto/                 # 请求/响应 DTO + 统一响应 ApiResponse<T>
├── entity/              # 数据库实体
├── exception/           # 自定义异常
├── interceptor/         # JWT 认证拦截器
├── mapper/              # MyBatis Mapper 接口
├── service/             # 业务逻辑接口
│   └── impl/            # 业务逻辑实现
└── ResumeApplication.java

src/main/resources/
├── application.yml      # 主配置
└── mapper/              # MyBatis XML 映射文件
```

## 环境要求

- JDK 17+
- Maven 3.6+
- MySQL 8.0+
- ghcr.io/cardboardci/pdf2htmlex:edge（pdf转html）
- Docker

## 快速开始

### 1. 初始化数据库

启动mysql镜像

```bash

docker run -d --restart=always --name mysql \
-v E:/mysql/data:/var/lib/mysql \
-v E:/mysql/mysql-files:/var/lib/mysql-files \
-v E:/mysql/log:/var/log/mysql \
-p 3306:3306 \
-e TZ=Asia/Shanghai \
-e MYSQL_ROOT_PASSWORD=123456 \
mysql:latest \
--character-set-server=utf8mb4 \
--collation-server=utf8mb4_general_ci

```

```bash
/var/lib/mysql：将数据文件夹挂载到主机
/var/log/mysql：将日志文件夹挂载到主机
-e MYSQL_ROOT_PASSWORD=123456：初始化123456用户的密码
--character-set-server=utf8mb4：设置字符集
--collation-server=utf8mb4_general_ci：排序方式

```

创建 MySQL 数据库：

```bash
# 上传备份 sql 到宿主机，复制宿主机备份 sql 到容器
docker cp E:/mysql/resume_db.sql mysql:/

# 进入 mysql 容器内部，导入sql。
docker exec -it mysql /bin/bash

mysql -uroot -p123456

create database resume_db;
use resume_db;
source /resume_db.sql;

```

启动时 `DataInitializer` 会自动创建表结构和默认管理员账号。

### 2. 启动 pdf2htmlex 容器

```bash
  # 先启动一个不干活的容器挂着：
  docker run -d --name pdf2htmlex -v E:\code\filesearch\pdf:/work ghcr.io/cardboardci/pdf2htmlex:edge tail -f /dev/null
  
  # 用完手动清理：

  docker rm -f pdf2htmlex
```

### 3. 配置环境变量（可选）

```bash
# 数据库（默认 root/123456）
export DB_USERNAME=root
export DB_PASSWORD=your_password

# JWT（生产环境务必修改）
export JWT_SECRET=your-secret-key-at-least-256-bits
export JWT_EXPIRATION=86400000
```

### 4. 启动应用

```bash
# 开发环境
mvn spring-boot:run

# 打包部署
mvn clean package -DskipTests
java -jar target/resume-backend-1.0.0.jar
```

应用启动后访问：
- **Swagger UI**: http://localhost:8080/swagger-ui/index.html
- **OpenAPI Spec**: http://localhost:8080/v3/api-docs

### 5. 登录

默认管理员账号：`admin` / `admin123`

```
POST /api/admin/login
{
  "username": "admin",
  "password": "admin123"
}
```

## API 概览

### 认证接口 (`/api/admin`)

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/admin/login` | 登录获取 JWT 令牌（IP 限流：每分钟 5 次） |
| POST | `/api/admin/logout` | 登出（令牌加入黑名单） |

### 简历接口 (`/api/resumes`) — 需要 JWT 认证

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/resumes/upload` | 上传 PDF 简历 |
| GET | `/api/resumes` | 分页获取简历列表 |
| GET | `/api/resumes/{id}` | 获取简历详情 |
| PUT | `/api/resumes/{id}` | 编辑简历信息及 HTML 内容 |
| GET | `/api/resumes/{id}/pdf` | 获取简历 HTML 内容（用于编辑器加载） |
| DELETE | `/api/resumes/{id}` | 删除简历 |

所有接口统一返回 `ApiResponse<T>` 格式：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": { ... }
}
```

## 核心功能

### PDF 上传与转换
- 上传 PDF 通过 Docker 容器中的 `pdftohtml`（poppler-utils）转换为 HTML
- 使用 `-c`（复杂模式）保留原始排版，`-s` 单文件输出，`-dataurls` 内联图片
- 原始 PDF 文件同时存储（`file_data` 字段，JSON 序列化时忽略）

### 富文本在线编辑
- 编辑器加载 HTML 内容进行可视化编辑
- 保存时通过 Jsoup 清洗 HTML：允许 `class`/`style` 属性、保留 `<style>` 标签、排除 `<head>` 防止文本泄漏
- 支持 base64 图片嵌入

### 分栏文本提取
- `PdfExtractionService` 通过 PDFBox 分析 PDF 布局
- 自动检测列边界，分离多栏文本（适配两栏/三栏简历排版）
- 支持 CJK 字体回退（SimHei、SimSun、KaiTi、Microsoft YaHei）

### HTML 导出 PDF
- 通过 Flying Saucer 将 HTML 内容渲染为 A4 PDF
- 自动内联 base64 图片、注册 Windows 中文字体
- 失败时回退为纯文本 PDF

## 认证流程

```
1. POST /api/admin/login → 验证用户名密码 → 返回 JWT 令牌
2. 后续请求携带 Authorization: Bearer <token>
3. AuthInterceptor 拦截 /api/resumes/** 路径验证令牌
4. POST /api/admin/logout → 令牌加入黑名单
```

- 密码使用 BCrypt 加密存储
- JWT 签名使用 HMAC-SHA256（HS384）
- 令牌黑名单定时清理过期条目

## 配置说明

| 配置项 | 环境变量 | 默认值 |
|--------|----------|--------|
| 数据库地址 | — | `jdbc:mysql://localhost:3306/resume_db` |
| 数据库用户 | `DB_USERNAME` | `root` |
| 数据库密码 | `DB_PASSWORD` | `123456` |
| JWT 密钥 | `JWT_SECRET` | 内置默认值（生产环境务必修改） |
| JWT 过期时间 | `JWT_EXPIRATION` | `86400000`（24小时） |
| 文件上传限制 | — | 10MB |
| 管理员默认密码 | — | `admin123`（首次启动自动创建） |

## 数据库表

### admin（管理员表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| username | VARCHAR(50) | 用户名 |
| password | VARCHAR(255) | BCrypt 加密密码 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

### resume（简历表）
| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键 |
| username | VARCHAR(50) | 姓名 |
| work_years | INT | 工作年限 |
| age | INT | 年龄 |
| gender | VARCHAR(10) | 性别 |
| resume_content | MEDIUMTEXT | HTML 内容 |
| file_data | MEDIUMBLOB | 原始 PDF 文件 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |
