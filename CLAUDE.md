# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Start (requires MySQL running with resume_db created via sql/init.sql)
mvn spring-boot:run

# Build
mvn clean package

# Run tests
mvn test
```

- Java 17+, Spring Boot 3.2.5, MyBatis 3.0.3, MySQL
- Edit `src/main/resources/application.yml` for DB connection and JWT config before running.

## API Documentation

Swagger UI available at `http://localhost:8080/swagger-ui/index.html` when running.
OpenAPI JSON spec at `http://localhost:8080/v3/api-docs`.

All `/api/resumes/**` endpoints require a JWT token. Use the "Authorize" button in Swagger UI to set the `BearerAuth` token after logging in.

## Architecture

Standard layered architecture — Controller → Service → Mapper (MyBatis) → MySQL.

- **Controller layer** (`controller/`): REST endpoints, request validation via `@Valid`. Returns `ApiResponse<T>` uniformly.
- **Service layer** (`service/`): Business logic. Throw `RuntimeException` for error cases — `GlobalExceptionHandler` catches and converts to `ApiResponse`.
- **Mapper layer** (`mapper/`): MyBatis interfaces under `com.resume.mapper`, XML mapping files under `resources/mapper/`.
- **Entities** (`entity/`): Plain Java POJOs matching DB tables. Fields use camelCase; MyBatis maps underscore-to-camel via `map-underscore-to-camel-case: true`.
- **DTOs** (`dto/`): Request/response objects with `jakarta.validation` constraints.
- **Config** (`config/`): Spring config, interceptor registration, global exception handler, data initializer.

## Auth Flow

1. `POST /api/admin/login` → returns JWT token
2. All `/api/resumes/**` requests are intercepted by `AuthInterceptor` — requires `Authorization: Bearer <token>` header
3. Token signed with HMAC-SHA256 using `jwt.secret` from `application.yml`
4. Admin password encrypted with BCrypt; default admin (`admin/admin123`) auto-created by `DataInitializer` on first startup

## Key Conventions

- All API responses use `ApiResponse<T>` with `{code, message, data}` format
- Business errors: throw `RuntimeException` (status 400); validation errors: `MethodArgumentNotValidException` (status 400); unhandled: 500
- DB columns use `snake_case`, Java fields use `camelCase` — auto-mapped by MyBatis config
- MyBatis mapper XML files in `resources/mapper/` match fully-qualified mapper interface name
