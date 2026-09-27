# AI Worklog — AI Gateway

> Ghi lại quá trình sử dụng AI trong phát triển project AI Gateway.
>
> **Note:** Prompt history is partially reconstructed from the available development context.
> Git history không có commits (branch `master` chưa có commit nào), nên thông tin được reconstruct từ source code và conversation context.

---

## Day 1 — 2026-09-26

### 1. AI Tools Used

| Tool | Role |
|------|------|
| **Antigravity** (Google DeepMind) | Primary AI assistant — architecture design, code generation, debugging, test writing |

> Không có bằng chứng về việc sử dụng thêm các AI tool khác (ChatGPT, Gemini, Copilot, v.v.) trong session này.

---

### 2. Prompts Used

> Prompt history is partially reconstructed from the available development context.

Trong Day 1, developer đã gửi ít nhất **2 prompt lớn** đến Antigravity:

**Prompt 1 — Basic Authentication (Register + Login only)**

```
Hãy tiếp tục project AI Gateway hiện tại.
Chỉ implement Authentication cơ bản: Register + Login.
Không làm Refresh Token, Redis hay Logout ở task này.

[Existing stack: Spring Boot, Java 17, Maven, PostgreSQL, Flyway, Spring Data JPA,
Spring Security, Lombok]

POST /api/v1/auth/register
POST /api/v1/auth/login

[Chi tiết validation, response format, architecture, exception handling...]
```

**Prompt 2 — Full Authentication (JWT + Redis + Refresh Token + Logout)**

```
Bạn đang làm việc trên project AI Gateway bằng Spring Boot.

Implement hoàn chỉnh authentication với:
  Register → BCrypt → Login → Access Token JWT + Refresh Token → Redis

POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout

[Chi tiết: JWT payload, Redis key pattern, token rotation, SecurityConfig,
JwtAuthenticationFilter, CustomUserDetailsService, DTOs, RefreshTokenService,
GlobalExceptionHandler, logging rules, test requirements...]
```

---

### 3. AI Suggestions

Antigravity đã đề xuất và implement các điểm sau trong Day 1:

#### Architecture

- **Package structure:** `controller / service / repository / dto/request / dto/response / exception / config / security / entity / enums`
- **Separation of concerns:** Controller chỉ validate + delegate, không chứa business logic
- **Dedicated `RefreshTokenService`** tách biệt Redis operations khỏi `AuthService`

#### Authentication Design

- **BCrypt** (`BCryptPasswordEncoder`) để hash password, không bao giờ lưu plain text
- **Access Token = JWT** signed với HMAC-SHA, payload gồm: `sub`, `userId`, `email`, `role`, `iat`, `exp`
- **Refresh Token = opaque string** format `{userId}:{tokenId}` — không phải JWT, để tránh expose thông tin trong token gửi đến client
- **Token Rotation:** mỗi lần `/refresh` phải delete token cũ và tạo token mới

#### JWT Design

- Sử dụng thư viện `io.jsonwebtoken:jjwt` version `0.12.6` (jjwt-api + jjwt-impl + jjwt-jackson)
- JWT secret đọc từ environment variable `JWT_SECRET` với fallback dev default
- Access Token TTL: 15 phút (900,000ms)
- Refresh Token TTL: 7 ngày (604,800,000ms)
- `JwtProperties` class dùng `@ConfigurationProperties(prefix = "jwt")` để bind config

#### Redis Design

- Redis key pattern: `auth:refresh:{userId}:{tokenId}`
- Redis value: `"active"` (string đơn giản, đủ để xác nhận sự tồn tại)
- TTL tự động qua Redis expire — khi hết TTL, Redis tự xóa key
- Dùng `StringRedisTemplate` (không cần custom serializer)

#### Security Filter Chain

- `JwtAuthenticationFilter extends OncePerRequestFilter`
- Filter extract Bearer token → validate → set `UsernamePasswordAuthenticationToken` vào `SecurityContextHolder`
- Nếu token invalid/absent: filter không reject, để Spring Security authorization layer xử lý
- `SessionCreationPolicy.STATELESS`, CSRF disabled

#### API Structure

```
POST /api/v1/auth/register  → Public
POST /api/v1/auth/login     → Public
POST /api/v1/auth/refresh   → Public
POST /api/v1/auth/logout    → Authenticated (Bearer token required)
```

#### Exception Handling

- `GlobalExceptionHandler` với `@RestControllerAdvice`
- Response format chuẩn: `{ timestamp, status, error, message, path }`
- `EmailAlreadyExistsException` → 409 Conflict
- `InvalidCredentialsException` → 401 Unauthorized
- `InvalidTokenException` → 401 Unauthorized
- `MethodArgumentNotValidException` → 400 Bad Request
- Fallback → 500 Internal Server Error
- **Không log password, passwordHash, accessToken, refreshToken, JWT secret**

#### Validation

- `RegisterRequest`: `@NotBlank @Email` cho email, `@NotBlank @Size(min=8, max=100)` cho password
- `LoginRequest`: `@NotBlank @Email` cho email, `@NotBlank` cho password
- `RefreshTokenRequest` / `LogoutRequest`: `@NotBlank` cho refreshToken

#### Testing Suggestions

- `AuthServiceTest` dùng `@ExtendWith(MockitoExtension.class)` — pure unit test, không cần DB/Redis
- `AuthControllerTest` dùng `@WebMvcTest` (phát hiện không available trong Spring Boot 4.1.1)
- Test coverage: register success, duplicate email, login success, wrong password, disabled user, refresh success, refresh revoked token, logout

#### Database

- Không thay đổi Flyway migration
- Không tạo bảng `refresh_tokens` — dùng Redis thay thế hoàn toàn

---

### 4. Developer Changes / Verification

**AI suggested (Prompt 1 — basic auth):**
- Implement chỉ Register + Login, không có JWT, không có Redis

**Developer changed (Prompt 2):**
- Yêu cầu AI mở rộng ngay sang full authentication với JWT + Refresh Token + Redis + Logout trong cùng Day 1
- Developer quyết định scope đầy đủ thay vì incremental approach mà Prompt 1 đề xuất

---

**AI suggested:**
- Dùng `@WebMvcTest` cho `AuthControllerTest`

**Developer verified (quan sát từ conversation):**
- Spring Boot 4.1.1 không có `@WebMvcTest` annotation trong package `org.springframework.boot.test.autoconfigure.web.servlet`
- Bug này được AI phát hiện và ghi nhận trong quá trình chạy `mvn clean test`
- Fix chưa hoàn thành cuối Day 1 (xem phần Remaining)

---

**AI suggested:**
- System `JAVA_HOME` là Java 8, cần dùng Java 17

**Developer verified:**
- Java 17 có sẵn tại `C:\Program Files\Java\jdk-17`
- Maven command cần run với `$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"`
- Chưa có cấu hình permanent JAVA_HOME cho project

---

**AI suggested:**
- `application.yaml` dùng fallback `${JWT_SECRET:default-dev-secret-...}` cho local dev

**Developer decision:**
- Chấp nhận cấu hình này (không override)

---

**Developer verified (Not confirmed yet):**
- `mvn clean test` chưa pass do vấn đề `@WebMvcTest` trong Spring Boot 4.1.1
- Application startup chưa được verify do vấn đề này

---

### 5. Bugs Found by AI

| Bug | Cause | Fix | Verification |
|-----|-------|-----|--------------|
| pom.xml có các test dependency không tồn tại (`spring-boot-starter-actuator-test`, `spring-boot-starter-flyway-test`, v.v.) | Developer/AI ban đầu đặt sai artifact ID cho test scope dependencies | AI replace bằng `spring-boot-starter-test`, `spring-security-test`, `h2` | Fixed trong pom.xml |
| Maven compile fail với "class file has wrong version 61.0, should be 52.0" | System `JAVA_HOME` trỏ đến JDK 1.8, nhưng Spring Boot 4.1.1 compiled với Java 17 | AI phát hiện, fix bằng cách set `$env:JAVA_HOME` trước khi chạy mvn | Verified — compile pass sau khi set đúng JAVA_HOME |
| `@WebMvcTest` không available trong Spring Boot 4.1.1 | Spring Boot 4.x đã loại bỏ hoặc thay đổi `@WebMvcTest` annotation | AI phát hiện, chưa có fix hoàn chỉnh cuối Day 1 | Not verified — còn trong trạng thái unresolved |

---

### 6. Day 1 Result

```
Completed:
  - Project structure: controller/service/repository/dto/exception/config/security packages
  - Database schema: V1__init_schema.sql (users, conversations, messages, ai_requests tables)
  - User entity với đầy đủ fields (id, email, passwordHash, role, enabled, createdAt, updatedAt)
  - UserRepository với findByEmail() và existsByEmail()
  - RegisterRequest, LoginRequest, RefreshTokenRequest, LogoutRequest DTOs
  - UserResponse, AuthResponse DTOs
  - UserRole enum (USER, ADMIN)
  - GlobalExceptionHandler với 4 exception types
  - EmailAlreadyExistsException, InvalidCredentialsException, InvalidTokenException
  - SecurityConfig (CSRF disabled, STATELESS session, JWT filter, permitAll cho auth endpoints)
  - JwtProperties (@ConfigurationProperties)
  - JwtService (generate, validate, extract claims — dùng jjwt 0.12.6)
  - JwtAuthenticationFilter (OncePerRequestFilter)
  - CustomUserDetailsService
  - RefreshTokenService (Redis: create, validate, delete, rotate)
  - AuthService (register, login, refresh, logout)
  - AuthController (POST /register, /login, /refresh, /logout)
  - AuthServiceTest (unit tests với Mockito — 8 test cases)
  - AuthControllerTest (viết xong nhưng có compilation issue)
  - application.yaml updated với Redis và JWT config
  - pom.xml updated với jjwt 0.12.6, spring-boot-starter-data-redis
  - .env.example created
  - docker-compose.yml updated với Redis service

Tested:
  - mvn compile: PASS (với JAVA_HOME=JDK17)
  - mvn clean test: FAIL (AuthControllerTest compilation error — @WebMvcTest not found)
  - Manual API test: Not verified (application not started)

Remaining:
  - Fix AuthControllerTest compilation error (@WebMvcTest không available trong Spring Boot 4.1.1)
  - Verify mvn clean test PASS
  - Manual end-to-end test các flow: register, login, refresh, logout, token rotation
  - Verify Redis key pattern và TTL sau login
  - Configure permanent JAVA_HOME trên development machine
```

---

## Day 2 — 2026-09-27

### 1. AI Tools Used

| Tool | Role |
|------|------|
| **Antigravity** (Google DeepMind) | Primary AI assistant — architecture design, code generation, debugging, rate limit implementation, end-to-end testing |

> Không có bằng chứng về việc sử dụng thêm các AI tool khác (ChatGPT, Gemini, Copilot, v.v.) trong session này.

---

### 2. Prompts Used

Trong Day 2, developer đã yêu cầu các tính năng chính sau:

**Prompt 1 — LLM Integration (OpenAI → Groq)**
```
Bạn đang làm việc trên project AI Gateway bằng Spring Boot.
Hãy giúp tôi chuyển đổi toàn bộ project Spring Boot hiện tại từ OpenAI sang Groq.
base-url phải là: https://api.groq.com/openai/v1
Model mặc định: llama-3.3-70b-versatile
API key dùng biến môi trường: ${AI_API_KEY} hoặc ${GROQ_API_KEY}
```

**Prompt 2 — Conversation & Message Entities**
```
Thiết kế và implement việc lưu lịch sử hội thoại: User -> Conversation -> Message
Message phải phân biệt được USER, ASSISTANT, SYSTEM.
Tạo Repository, Service.
```

**Prompt 3 — AI Chat API + AI Request Logging**
```
Tạo API POST /ai/chat.
Flow: JWT -> Get User -> Conversation -> LLM API -> Save Message -> Save AI Request Log -> Save Token Usage.
```

**Prompt 4 — Rate Limit + Final Verification**
```
Implement rate limiting cho POST /ai/chat.
Rate limit phải được áp dụng sau JWT authentication, theo user.
Nếu vượt rate limit -> 429 Too Many Requests.
Nếu đã có Redis thì ưu tiên Redis. Cuối cùng thực hiện end-to-end test flow.
```

---

### 3. AI Suggestions

Antigravity đã đề xuất và implement các điểm sau trong Day 2:

#### LLM Provider Abstraction
- Abstract `AiService` và `LlmProvider` để dễ dàng switch giữa các AI Models.
- Đổi cấu hình gọi API sang dùng URL của Groq nhưng format của OpenAI qua WebClient/RestClient.
- Inject `GROQ_API_KEY` từ `.env` via `spring.config.import`.

#### Conversation History Structure
- Tạo Entity `Conversation` (chứa `userId`, `title`, timestamps).
- Tạo Entity `Message` (chứa `conversationId`, `role` (USER/ASSISTANT/SYSTEM), `content`, tokens).
- Tích hợp lịch sử hội thoại vào `ChatRequest` để LLM hiểu context trước đó (limit contextSize).

#### Rate Limiting với Redis
- Implement fixed-window rate limit dùng Redis `opsForValue().increment()` kết hợp với `expire()` để atomicity.
- Redis key format: `ratelimit:ai:chat:user:{userId}`.
- Giới hạn: 5 requests / 60s (có thể cấu hình trong `AiProperties`).
- Trả về exception `RateLimitExceededException` được handle bởi `GlobalExceptionHandler` với status `429 Too Many Requests`.

#### End-to-End Verification
- Phát hiện lỗi Spring Boot không parse được TimeZone `Asia/Saigon` khi kết nối Postgres, AI đã sửa bằng cách inject `UTC` TimeZone vào process.
- Tạo script PowerShell tự động test flow `Register` -> `Login` -> Call `/ai/chat` 6 lần liên tiếp.
- Xác nhận các API hoạt động, trả lời bằng AI Model từ Groq và block Request thứ 6 thành công với mã 429.

---

### 4. Bugs Found by AI

| Bug | Cause | Fix | Verification |
|-----|-------|-----|--------------|
| Spring Boot startup error `FATAL: invalid value for parameter "TimeZone": "Asia/Saigon"` | PostgreSQL JDBC driver không tương thích với múi giờ default của môi trường Windows | Chạy script JVM với tham số `-Duser.timezone=UTC` hoặc `$env:TZ="UTC"` | Verified — App khởi động thành công |
| Lỗi 500 khi login | Token `JWT_SECRET` không lấy được do plugin `spring-dotenv` không hoạt động tốt với phiên bản Spring hiện tại | Thay thế thư viện dotenv bằng config native của Spring `spring.config.import=optional:file:.env[.properties]` | Verified — JWT tạo thành công |
| `Mockito` test RestClient `body()` ambiguous call | `RestClient.RequestBodySpec.body(Object)` bị trùng signature khi compile Test | Rút gọn `any()` bằng `any(Object.class)` | Verified — `mvn test` passing (45/45 tests) |

---

### 5. Day 2 Result

```
Completed:
  - Chuyển đổi thành công OpenAI sang Groq (llama-3.3-70b-versatile).
  - Hoàn thiện database schemas cho Conversation và Message, AI_Request.
  - POST /api/v1/ai/chat logic hoàn chỉnh, tính toán tổng Token Usage.
  - Implement Redis Rate Limiting 429.
  - End-To-End Test passed (JWT, Chat, Rate Limit).

Tested:
  - mvn clean test: PASS (45 tests passed)
  - Manual API / e2e_test.ps1: PASS (Đạt 429 cho Request thứ 6).

Remaining:
  - Project sẵn sàng deploy hoặc bổ sung tính năng mới tùy ý.
```

---

*Worklog được tạo ngày 2026-09-27. Thông tin được reconstruct từ source code và conversation context.*
