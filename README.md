# 🎫 HelpDesk Enterprise Backend REST API

[![Java 17](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot 4.1.1](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring Security](https://img.shields.io/badge/Security-Spring%20Security%207-blue.svg)](https://spring.io/projects/spring-security)
[![Database](https://img.shields.io/badge/Database-MySQL%20%7C%20Flyway-blue.svg)](https://flywaydb.org/)
[![Architecture](https://img.shields.io/badge/Architecture-Event--Driven%20%7C%20Cached-purple.svg)]()
[![Tests](https://img.shields.io/badge/Tests-40%20Passed-success.svg)]()

A production-grade, enterprise-ready IT Operations and HelpDesk Ticket Management backend built with **Spring Boot 4**, **Java 17**, and **Spring Security**. Engineered for high reliability, concurrent workloads, real-world data isolation, and regulatory audit compliance.

---

## 🌟 Architectural Highlights

```mermaid
flowchart TD
    Client["Client / Frontend"] --> Ingress["HTTP / Reverse Proxy"]
    
    subgraph Security_Filter_Pipeline["Security Filter Pipeline"]
        Ingress --> ReqId["RequestIdFilter (MDC X-Request-Id)"]
        ReqId --> RateLimiter["RateLimitFilter (Token Bucket)"]
        RateLimiter --> JwtAuth["JwtFilter (Access Token Validation)"]
    end

    subgraph Controller_Layer["REST Controller Layer"]
        JwtAuth --> AuthCtrl["AuthController"]
        JwtAuth --> TicketCtrl["TicketController"]
        JwtAuth --> AttachCtrl["TicketAttachmentController"]
        JwtAuth --> CommentCtrl["CommentController"]
        JwtAuth --> UserCtrl["UserController"]
        JwtAuth --> AuditCtrl["AuditLogController"]
    end

    subgraph Service_And_Cache["Service & Cache Layer"]
        TicketCtrl --> TicketSvc["TicketService (@Cacheable / @CacheEvict)"]
        TicketSvc --> Cache["In-Memory Cache (ticketSummary, agentWorkload)"]
        TicketSvc --> EventPub["ApplicationEventPublisher"]
    end

    subgraph Async_Event_System["Asynchronous Event Worker"]
        EventPub -.->|AFTER_COMMIT| AsyncListener["TicketEventListener (@Async)"]
        AsyncListener --> ThreadPool["ThreadPoolTaskExecutor (helpdesk-async-*)"]
        ThreadPool --> MailSvc["NotificationService (SMTP / Email)"]
    end

    subgraph SLA_Engine["SLA Escalation Engine"]
        SlaCron["@Scheduled Cron Worker"] --> SlaEscalation["SlaEscalationService"]
        SlaEscalation --> AuditRepo
    end

    subgraph Persistence_Layer["Persistence Layer (Spring Data JPA / Flyway)"]
        TicketSvc --> TicketRepo["TicketRepository (@Version Optimistic Lock)"]
        TicketRepo --> MySQL[("MySQL 8.0 Primary DB")]
        AttachCtrl --> Storage["Secure Disk Storage (/uploads)"]
        AuditCtrl --> AuditRepo["AuditLogRepository"]
        AuditRepo --> MySQL
    end
```

---

## 🚀 Key Production Features

### 1. 🛡️ Hardened Authentication & JWT Token Rotation
- **Zero-State JWT Authentication**: Short-lived access tokens with cryptographic HMAC signing.
- **Refresh Token Rotation & Reuse Detection**: Every refresh token issuance invalidates the prior token. If an invalidated refresh token is reused, the entire token family is immediately revoked to isolate compromised credentials.
- **Anti-Enumeration Password Recovery**: Self-service `/api/auth/forgot-password` generates single-use 15-minute cryptographic reset tokens. Responses are non-enumerable to prevent account probing. Successful reset triggers mass revocation of all active sessions.

### 2. ⚡ Concurrency Control & Optimistic Locking
- **`@Version` Optimistic Concurrency**: Ticket entities utilize database versioning to prevent lost updates when multiple agents update the same ticket simultaneously.
- **HTTP 409 Conflict Resolution**: Catches `OptimisticLockingFailureException` and returns human-readable diagnostic error payloads instructing callers to reload current state.

### 3. ⏱️ Dynamic SLA Engine & Background Escalation
- **Priority-Driven Deadlines**: Automatically calculates resolution targets upon creation (Urgent: 4 hours, High: 24 hours, Medium: 72 hours).
- **Scheduled Escalation Worker**: An automated background job (`@Scheduled(cron = "${app.sla.check-cron}")`) continuously monitors and flags breached tickets using composite indexing (`idx_tickets_sla_breach`).
- **Audit Logging of Breaches**: Automatically appends immutable SLA breach events to the ticket audit trail.

### 4. 📬 Asynchronous Decoupled Event System
- **Transactional Domain Events**: Emits `TicketStatusChangedEvent` and `TicketAssignedEvent` upon state transitions.
- **After-Commit Execution**: Event listeners use `@TransactionalEventListener(phase = AFTER_COMMIT)` to ensure emails and side-effects are only dispatched if the database transaction commits successfully.
- **Dedicated Thread Pool**: Bound to an isolated `ThreadPoolTaskExecutor` (`helpdesk-async-*`) so external notification latency never degrades API response times.

### 5. 📎 Secure Multipart File Attachments
- **MIME Whitelisting & Size Caps**: Accepts documents and images (`application/pdf`, `image/png`, `image/jpeg`, `text/plain`) with strict 10MB request filtering.
- **Path-Traversal Prevention**: Sanitizes file paths, stores payloads using randomized UUIDs, and enforces caller authorization before streaming file resources back.

### 6. 📊 Analytical Query Caching
- **Multi-Level Metric Caching**: Highly frequent read endpoints (`/api/tickets/summary` and `/api/tickets/agent-workload`) are cached in-memory with `@Cacheable`.
- **Targeted Cache Eviction**: Every ticket creation, status update, assignment, or SLA breach immediately purges stale metrics via `@CacheEvict(allEntries = true)`.

### 7. 🔍 Enterprise Observability & Tracing
- **Correlation IDs (MDC)**: Generates or propagates `X-Request-Id` across all HTTP logs and responses.
- **Sliding-Window Rate Limiting**: In-memory token bucket per IP to protect public auth endpoints from abuse.
- **Spring Boot Actuator & Prometheus**: Metrics exposed at `/actuator/prometheus` for Grafana scraping, health checks at `/actuator/health`.
- **Audit Retention Policy**: Configurable retention window with an automated/admin-triggered cleanup job (`DELETE /api/audit/cleanup`).

---

## 📂 Project Structure

```text
helpdesk-backend/
├── docker/                             # Docker deployment configurations
│   ├── mysql/init/                     # Database seed scripts
│   ├── nginx/                          # Reverse proxy config
│   └── docker-compose.yml              # Local production-like orchestrator
├── src/
│   ├── main/
│   │   ├── java/com/example/helpdesk_backend/
│   │   │   ├── bootstrap/              # Seed data initialization
│   │   │   ├── config/                 # Async, Cache, and OpenAPI configuration
│   │   │   ├── controller/             # REST API Controllers with Swagger annotations
│   │   │   ├── dto/                    # Request & Response Data Transfer Objects
│   │   │   ├── entity/                 # JPA Entities (Ticket, User, Attachment, Token, etc.)
│   │   │   ├── enums/                  # Status, Priority, Role enums
│   │   │   ├── event/                  # Domain Events and @Async Event Listeners
│   │   │   ├── exception/              # Global Exception Handling & Custom Errors
│   │   │   ├── repository/             # Spring Data JPA Repositories
│   │   │   ├── security/               # JWT filters, Rate limiters, MDC Tracers
│   │   │   └── service/                # Business logic, SLA worker, Attachments, Caching
│   │   └── resources/
│   │       ├── db/migration/           # Versioned Flyway SQL migrations (V1 to V4)
│   │       ├── application.properties  # Base configuration
│   │       └── application-prod.properties
│   └── test/                           # 40 Unit & Integration tests
│       └── java/com/example/helpdesk_backend/
│           ├── event/                  # Event listener tests
│           ├── integration/            # MockMvc RBAC, Auth, and Security integration tests
│           ├── security/               # JWT & Filter unit tests
│           └── service/                # Service layer, Cache, and SLA engine tests
└── pom.xml                             # Maven build dependencies
```

---

## 📑 Database Schema & Migrations

The database schema is managed via **Flyway** migrations:

| Migration | Scope | Description |
|---|---|---|
| `V1__init_schema.sql` | Core Tables | Creates `users`, `tickets`, and `comments` tables. |
| `V2__refresh_tokens_and_audit_logs.sql` | Security & Audit | Creates `refresh_tokens`, `audit_logs`, `password_reset_tokens`, and `ticket_attachments`. |
| `V3__performance_indexes.sql` | Query Optimization | Composite indexing on ticket status, priority, and foreign keys. |
| `V4__add_optimistic_locking_and_sla.sql` | Concurrency & SLA | Adds `version` column for optimistic locking, `sla_due_at`, `sla_breached` flags, and breach composite index. |

---

## 📡 REST API Reference

Interactive Swagger UI documentation is available at **`/swagger-ui.html`**.

### Authentication (`/api/auth`)
| Method | Endpoint | Access | Description |
|---|---|---|---|
| `POST` | `/api/auth/login` | Public | Authenticates user; returns JWT access & refresh tokens. |
| `POST` | `/api/auth/refresh` | Public | Rotates refresh token; issues new access token. |
| `POST` | `/api/auth/logout` | Authenticated | Revokes refresh token and ends session. |
| `POST` | `/api/auth/forgot-password` | Public | Requests single-use 15-minute password reset link. |
| `POST` | `/api/auth/reset-password` | Public | Confirms reset token and sets new password. |

### Tickets (`/api/tickets`)
| Method | Endpoint | Access | Description |
|---|---|---|---|
| `GET` | `/api/tickets` | Scoped | Retrieves tickets for current user (Customer: own, Agent: assigned, Admin: all). |
| `GET` | `/api/tickets/paged` | Scoped | Paginated and filtered search by status, priority, or title keyword. |
| `POST` | `/api/tickets` | Authenticated | Creates a new ticket and schedules dynamic SLA targets. |
| `GET` | `/api/tickets/{id}` | Scoped | Retrieves ticket details enforcing IDOR boundaries. |
| `PUT` | `/api/tickets/{id}` | Owner / Admin | Updates ticket; guarded by `@Version` optimistic locking (HTTP 409 on race). |
| `DELETE` | `/api/tickets/{id}` | Owner / Admin | Deletes ticket and associated relations. |
| `PUT` | `/api/tickets/{id}/status` | Agent / Admin | Updates ticket status along valid lifecycle path; dispatches async event. |
| `PUT` | `/api/tickets/{id}/assign` | Admin | Assigns ticket to an agent; dispatches assignment event. |
| `GET` | `/api/tickets/summary` | Admin | Cached ticket volume summary grouped by status. |
| `GET` | `/api/tickets/agent-workload` | Admin | Cached active ticket count breakdown per agent. |

### Attachments (`/api/tickets/{ticketId}/attachments`)
| Method | Endpoint | Access | Description |
|---|---|---|---|
| `POST` | `/api/tickets/{ticketId}/attachments` | Scoped | Uploads file attachment (multipart/form-data, max 10MB). |
| `GET` | `/api/tickets/{ticketId}/attachments` | Scoped | Lists all attachment metadata for a ticket. |
| `GET` | `/api/tickets/{ticketId}/attachments/{id}` | Scoped | Downloads file stream with content-disposition header. |

### Comments (`/api/tickets/{ticketId}/comments`)
| Method | Endpoint | Access | Description |
|---|---|---|---|
| `POST` | `/api/tickets/{ticketId}/comments` | Scoped | Appends a comment to a ticket. |
| `GET` | `/api/tickets/{ticketId}/comments` | Scoped | Lists chronological comments for a ticket. |

### Users & Administration (`/api/users` & `/api/audit`)
| Method | Endpoint | Access | Description |
|---|---|---|---|
| `POST` | `/api/users` | Public | Registers a new user account (`ROLE_CUSTOMER` by default). |
| `GET` | `/api/users` | Admin | Lists all user accounts. |
| `GET` | `/api/users/{id}` | Admin | Gets user details by ID. |
| `PUT` | `/api/users/{id}/role` | Admin | Updates user authority (`CUSTOMER`, `AGENT`, `ADMIN`). |
| `DELETE` | `/api/users/{id}` | Admin | Deletes user account. |
| `GET` | `/api/audit/tickets/{ticketId}` | Admin | Chronological history of ticket status and SLA changes. |
| `DELETE` | `/api/audit/cleanup` | Admin | Purges audit logs older than the retention threshold. |

---

## 🧪 Comprehensive Test Suite

The project includes **40 automated tests** spanning unit, integration, and security layers:

```bash
# Run all tests via Maven Wrapper
./mvnw clean test

# Windows Powershell
.\mvnw.cmd clean test
```

### Test Breakdown:
- **`AuthIntegrationTest`**: Complete MockMvc verification of user registration, login, token refresh rotation, and compromise isolation on token reuse.
- **`TicketSecurityIntegrationTest`**: End-to-end RBAC tests verifying IDOR protection, Customer vs Admin permission boundaries, invalid lifecycle state transitions, and comment scoping.
- **`TicketCacheTest`**: Spring Boot integration test asserting that analytical queries hit in-memory cache and are automatically evicted upon ticket state changes.
- **`SlaEscalationServiceTest`**: Verification of background breach detection, overdue computation, and audit event insertion.
- **`TicketAttachmentServiceTest`**: Tests MIME type validation, file traversal defense, and disk streaming.
- **`PasswordResetServiceTest`**: Tests single-use token lifecycle, expiration boundaries, and mass session invalidation.
- **`TicketEventListenerTest`**: Validates asynchronous event delivery on status changes and assignments.

---

## 🛠️ Local Development & Quickstart

### Prerequisites
- Java 17+ SDK
- Maven 3.8+
- MySQL 8.0 (or run via Docker)

### 1. Clone & Configure
```bash
git clone https://github.com/HarshadRawat/helpdesk-backend.git
cd helpdesk-backend/helpdesk-backend
```

### 2. Configure Environment Secrets
Create a `.env` file or export environment variables:
```bash
export DB_URL=jdbc:mysql://localhost:3306/helpdesk_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
export DB_USERNAME=root
export DB_PASSWORD=your_secure_password
export JWT_SECRET=your-32-character-secret-key-for-jwt-auth!
export SPRING_PROFILES_ACTIVE=dev
```

### 3. Run Locally
```bash
./mvnw spring-boot:run
```
The server will start at `http://localhost:8080`.
Explore the API documentation at `http://localhost:8080/swagger-ui.html`.

---

## 🐳 Docker Deployment

The backend includes a production-ready multi-container Docker Compose configuration:

```bash
cd docker
docker compose up -d --build
```

Services launched:
- **`helpdesk-backend`**: Spring Boot application running on port `8080` (management on `8081`).
- **`mysql`**: MySQL 8.0 server with automated initialization scripts and health probes.
- **`nginx`**: Reverse proxy routing requests with rate-limiting and connection buffering.
- **`mailpit`**: Mock SMTP server and web interface for inspecting sent emails at `http://localhost:8025`.

---

## 📜 Production Readiness Checklist

- [x] **Stateless Security**: JWT tokens with refresh rotation and reuse prevention.
- [x] **Data Isolation**: Role-based access control protecting against Insecure Direct Object References (IDOR).
- [x] **Concurrency Safety**: Optimistic locking preventing lost updates under concurrent access.
- [x] **Decoupled Architecture**: Asynchronous transactional event listener with isolated thread pool.
- [x] **Service Level Agreements**: Automated background escalation engine with composite DB indexing.
- [x] **Performance Optimization**: In-memory caching for expensive analytical aggregates with automatic eviction.
- [x] **File Safety**: MIME whitelist enforcement, file-size guards, and sanitized disk storage.
- [x] **Observability**: Prometheus metrics export, structured MDC request correlation, and audit logging.
- [x] **Automated Testing**: 40 tests covering unit, MockMvc security, caching, and domain logic.
- [x] **Interactive Documentation**: OpenAPI 3.0 / Swagger UI integration with JWT Bearer support.
