# AGENTS.md

## Project snapshot

- Java 17 + Spring Boot 4 backend in `src/main/java/com/example/helpdesk_backend`.
- Maven wrapper is the canonical build entrypoint (`./mvnw` on Unix, `mvnw.cmd` on Windows).
- Main runtime config is `src/main/resources/application.properties`; secrets should come from env vars or local external config, not hardcoded values.
- Flyway migrations live in `src/main/resources/db/migration/`; the refresh-token hardening migration is `V3__harden_refresh_token_rotation.sql`.

## Build, run, and test

- Run the app locally:
  - `./mvnw spring-boot:run`
  - `mvnw.cmd spring-boot:run`
- Run the test suite:
  - `./mvnw test`
  - `mvnw.cmd test`
- Docker stack is defined in `docker-compose.yml` and starts the backend, MySQL, and Nginx proxy:
  - `docker compose up --build`
- Test configuration uses H2 in-memory DB via `HelpdeskBackendApplicationTests`; do not assume MySQL is required for a unit/integration test run.

## Architecture and code conventions

- The codebase follows a layered architecture: `controller -> service -> repository -> entity`.
- Typical service methods are transactional and receive DTOs or IDs from controllers; entity access is kept in the repository layer.
- Prefer constructor injection and `final` fields. Example: `AuthController` depends on `AuthService`, and `AuthService` depends on `UserRepository`, `RefreshTokenService`, `PasswordEncoder`, and `JwtService`.
- DTOs are the API boundary; validation annotations from Jakarta Bean Validation belong on request objects (`LoginRequest`, `RefreshTokenRequest`, ticket/user DTOs), not on entities.
- Global exception handling and centralized security rules are the default pattern; keep business rules in services rather than controllers.

## Key package map

- `controller/`: REST endpoints (`AuthController`, `TicketController`, `UserController`, etc.)
- `service/`: business logic (`AuthService`, `TicketService`, `CommentService`, `RefreshTokenService`, etc.)
- `repository/`: Spring Data JPA repositories (`UserRepository`, `RefreshTokenRepository`, etc.)
- `entity/`: persistence models (`User`, `Ticket`, `Comment`, `RefreshToken`)
- `dto/`: API request/response objects
- `security/`: `SecurityConfig`, `JwtFilter`, `JwtService`, `RateLimitFilter`
- `exception/`: custom exceptions and centralized error handling

## Security and authentication patterns

- Authentication is JWT-based. `SecurityConfig` adds `RateLimitFilter` and `JwtFilter` before `UsernamePasswordAuthenticationFilter` and defines endpoint access rules.
- Public auth endpoints are under `/api/auth`:
  - `POST /api/auth/login`
  - `POST /api/auth/refresh`
  - `POST /api/auth/logout`
  - password-reset and email-verification endpoints are also permitted
- Access and refresh tokens are generated from `JwtService`. Access tokens carry user id + role; refresh tokens include a UUID-style `jti` token ID and a `tokenType` claim.
- Refresh-token lifecycle is intentionally owned by `RefreshTokenService`, not by `AuthService` directly. The project now treats refresh token rotation and reuse detection as a first-class security mechanism.

### Refresh-token lifecycle (current project pattern)

- `RefreshTokenService.issueToken(User)` creates a JWT refresh token, hashes it, and persists it in `refresh_tokens`.
- `RefreshTokenService.rotate(String)` validates by `token_id` and `token_hash`, rejects expired/revoked tokens, issues a new refresh token, and marks the previous token as revoked with `replaced_by_token_id`.
- Reuse detection is enforced by checking `token_id` and the hashed token value; if a previously-used token shows up again, the service revokes all active tokens for that user and throws `InvalidLoginException`.
- The database schema now stores:
  - `token_id`
  - `token_hash`
  - `expires_at`
  - `revoked_at`
  - `replaced_by_token_id`
- Follow this pattern when changing auth behavior: update `RefreshTokenService` and the `RefreshToken`/`RefreshTokenRepository` model together, then adjust Flyway migrations if schema changes are required.

## Role-based authorization guidance

- Role checks are enforced through `SecurityConfig` for coarse-grained route access and through service-level logic for ownership and assignment rules.
- Example: ticket access is scoped by user ownership or assignment (`UserController`, `TicketService`, `CommentService`), not by trusting client-supplied IDs.
- Do not add auth checks in controllers that bypass the domain service rules; the project expects the service layer to enforce business permissions.

## Testing conventions

- The repository currently contains a smoke test in `src/test/java/com/example/helpdesk_backend/HelpdeskBackendApplicationTests.java`.
- Use the Spring Boot test context with the embedded H2 database for application-level validation, unless a narrower repository/service test clearly calls for a different setup.
- When adding tests for auth or refresh-token rotation, model them against the current `RefreshTokenService` behavior: issue, rotate, verify reuse detection, and invalid/expired token handling.

## Practical guidance for AI agents

- Before changing auth, look at `AuthService`, `RefreshTokenService`, `JwtService`, `RefreshToken`, and `RefreshTokenRepository` together; they form the current security contract.
- Before changing endpoint behavior, inspect the matching controller and the `SecurityConfig` route rules together.
- When changing database schema, update the migration under `src/main/resources/db/migration` and keep the `refresh_tokens` table semantics aligned with the Java model.
- Keep changes focused to the relevant layer: controller API changes, service logic changes, repository queries, or migration updates, without mixing unrelated concerns.

