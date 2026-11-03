# HelpDesk Backend

A production-oriented Spring Boot backend for a HelpDesk and ticket management system. The application provides secure authentication, role-based access control, ticket lifecycle management, audit support, and a Docker-ready deployment setup.

This project is built as a backend-only API service and is designed for real-world ticket workflows in an IT support environment.

## Overview

The system models a typical internal support workflow:

- users create support tickets
- agents manage assigned tickets
- admins oversee assignments and ticket operations
- the API enforces ownership and role-based access rules
- users authenticate through JWT tokens
- the system persists data using MySQL and Flyway migrations

## Features

- User registration and login
- JWT-based authentication and authorization
- Role-based access for CUSTOMER, AGENT, and ADMIN roles
- Ticket creation, update, assignment, and deletion
- Ticket status and priority management
- Comment support on tickets
- Pagination and filtering for ticket queries
- Validation and centralized exception handling
- BCrypt password hashing
- Refresh-token rotation and reuse detection for secure auth flows
- Request ID tracking and rate limiting support
- Audit logging and admin workload reporting
- Docker Compose deployment support

## Tech Stack

| Technology | Purpose |
| --- | --- |
| Java 17 | Backend runtime |
| Spring Boot 4 | Application framework |
| Spring Web MVC | REST API development |
| Spring Data JPA | Persistence layer |
| Spring Security | Authentication and authorization |
| JWT | Token-based authentication |
| MySQL | Primary database |
| Flyway | Database migrations |
| Hibernate | ORM |
| Maven | Dependency management and build |
| Docker / Docker Compose | Containerized local deployment |
| Actuator | Health and monitoring |
| H2 | In-memory test database |

## Architecture

The application follows a standard layered backend structure:

```text
Client
  ↓
Controller Layer
  ↓
Service Layer
  ↓
Repository Layer
  ↓
JPA / Hibernate
  ↓
MySQL / H2
```

This keeps responsibilities separated between HTTP handling, business logic, data access, and persistence.

## Project Structure

```text
src/
├── main/
│   ├── java/com/example/helpdesk_backend/
│   │   ├── bootstrap/
│   │   ├── controller/
│   │   ├── dto/
│   │   ├── entity/
│   │   ├── exception/
│   │   ├── repository/
│   │   ├── security/
│   │   ├── service/
│   │   └── HelpdeskBackendApplication.java
│   └── resources/
│       ├── application.properties
│       ├── application-dev.properties
│       ├── application-prod.properties
│       └── db/migration/
└── test/
    └── java/com/example/helpdesk_backend/
```

## Authentication and Authorization

The API uses JWT-based authentication with Spring Security.

Core security behavior includes:

- login endpoint for generating auth tokens
- refresh-token rotation and reuse detection
- role-based access checks for ticket and user endpoints
- request filtering and rate limiting
- audit tracking for operational visibility

## User Roles

### CUSTOMER
- create tickets
- view their own tickets
- update their own tickets
- add comments to their tickets

### AGENT
- view assigned tickets
- process assigned ticket workflows
- update status and comments

### ADMIN
- view all tickets and data
- perform operational assignments
- manage high-level ticket administration

## Ticket Lifecycle

The system supports a structured ticket workflow:

```text
OPEN → IN_PROGRESS → RESOLVED → CLOSED
```

The business logic prevents invalid transitions and enforces ownership/assignment rules before state changes are allowed.

## Core API Endpoints

### Authentication

| Method | Endpoint | Description |
| --- | --- | --- |
| POST | `/api/auth/login` | User login |
| POST | `/api/auth/refresh` | Refresh access token |
| POST | `/api/auth/logout` | Logout user and revoke refresh token |

### Users

| Method | Endpoint | Description |
| --- | --- | --- |
| GET | `/api/users` | List users |
| GET | `/api/users/{id}` | Get user by ID |
| POST | `/api/users` | Create user |
| PUT | `/api/users/{id}` | Update user |

### Tickets

| Method | Endpoint | Description |
| --- | --- | --- |
| POST | `/api/tickets` | Create ticket |
| GET | `/api/tickets` | View tickets for the authenticated user |
| GET | `/api/tickets/{id}` | Get ticket by ID |
| PUT | `/api/tickets/{id}` | Update ticket |
| DELETE | `/api/tickets/{id}` | Delete ticket |
| PUT | `/api/tickets/{id}/assign` | Assign ticket to agent |
| PUT | `/api/tickets/{id}/status` | Update ticket status |

## Local Development Setup

### Prerequisites

- Java 17+
- Maven
- MySQL (for local runtime)
- Docker (optional, for containerized local setup)

### Clone the Repository

```bash
git clone <repository-url>
cd helpdesk-backend
```

### Configure Environment Variables

Use environment variables or a local `.env` file for runtime secrets.

Example:

```bash
export DB_URL=jdbc:mysql://localhost:3306/helpdesk_db
export DB_USERNAME=root
export DB_PASSWORD=your_secure_password
export JWT_SECRET=your_long_secure_secret_key
export SPRING_PROFILES_ACTIVE=dev
```

### Run the Application

Using Maven wrapper:

```bash
./mvnw spring-boot:run
```

On Windows:

```powershell
mvnw.cmd spring-boot:run
```

The application starts on the configured server port, typically:

```text
http://localhost:8080
```

## Docker Setup

The project includes Docker Compose support for running the backend and MySQL together.

```bash
docker compose up --build
```

This is suitable for local validation and testing deployment behavior in a production-like environment.

## Database and Migration Notes

The backend uses Flyway for schema management. Database migrations live under:

```text
src/main/resources/db/migration/
```

This keeps schema changes repeatable and consistent across environments.

## Security Notes

Before production deployment, keep secrets outside source control.

Recommended approaches:

- environment variables
- Docker secrets
- cloud secret managers such as AWS Secrets Manager, Azure Key Vault, or HashiCorp Vault

Do not commit production credentials, private keys, or real JWT secrets into the repository.

## Testing

The project includes Spring Boot test coverage for core application startup and backend security logic.

Run the full test suite with:

```bash
./mvnw test
```

On Windows:

```powershell
mvnw.cmd test
```

## Health and Monitoring

The app exposes Spring Boot Actuator endpoints for health and runtime diagnostics.

Example:

```bash
curl http://localhost:8080/actuator/health
```

## Production Readiness

This backend is structured for real application use and includes several production-oriented patterns, including:

- environment-based configuration
- JWT lifecycle security
- validation and exceptions
- request tracking
- rate limiting support
- Docker-based deployment setup
- migration-based schema management

## License

This project is provided as a backend development sample and is intended for learning and portfolio use.

## Contact

For questions or collaboration, please reach out via the project repository or contact information linked to the repository owner.

---

## 👨‍💻 Author

**Harshad Rawat**

Java | Spring Boot | Backend Development | DSA