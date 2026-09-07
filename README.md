# HelpDesk - Ticket Management System

A backend REST API for managing IT support tickets using Spring Boot, Spring Security, JWT authentication, JPA/Hibernate, and MySQL.

The application simulates a real-world IT HelpDesk system where customers can raise support tickets, agents can work on assigned tickets, and administrators can manage tickets and assignments.

---

## 📌 Project Overview

In a typical organization, employees may face technical issues such as:

- Wi-Fi not working
- Printer problems
- Software issues
- System or network problems

Instead of directly contacting the IT team, users can create a support ticket through the HelpDesk system.

The ticket can then be assigned to an agent and progresses through different stages until the issue is resolved and closed.

---

## ✨ Features

- User creation and login
- JWT-based authentication
- Role-based authorization
- Three user roles:
    - CUSTOMER
    - AGENT
    - ADMIN
- Create, view, update, and delete tickets
- Assign tickets to agents
- Ticket status management
- Ticket priority management
- Comments on tickets
- Pagination
- Ticket filtering by status and priority
- Input validation
- Global exception handling
- Password hashing using BCrypt
- MySQL database integration
- JPA/Hibernate ORM

---

## 🛠️ Tech Stack

| Technology | Purpose |
|------------|---------|
| Java | Backend programming |
| Spring Boot | Backend framework |
| Spring Web | REST API development |
| Spring Data JPA | Database access |
| Hibernate | ORM |
| Spring Security | Authentication and authorization |
| JWT | Token-based authentication |
| MySQL | Relational database |
| Maven | Dependency management |
| Postman | API testing |
| IntelliJ IDEA | Development |

---

## 🏗️ Architecture

The project follows a layered architecture:

```text
                    Client / Postman
                           |
                           ↓
                    Controller Layer
                           |
                           ↓
                     Service Layer
                           |
                           ↓
                   Repository Layer
                           |
                           ↓
                    JPA / Hibernate
                           |
                           ↓
                         MySQL
```

### Controller Layer

Handles HTTP requests and responses.

### Service Layer

Contains the application's business logic, such as:

- Ticket ownership checks
- Role-based access
- Ticket assignment
- Status transition validation

### Repository Layer

Uses Spring Data JPA to communicate with the database.

### Entity Layer

Represents the main database entities:

- User
- Ticket
- Comment

### DTO Layer

DTOs are used to control the data sent to and received from REST APIs instead of directly exposing entities.

---

## 👥 User Roles

### CUSTOMER

Customers can:

- Create tickets
- View their own tickets
- Update their own tickets
- Delete their own tickets
- Add comments

A customer cannot access another customer's ticket.

### AGENT

Agents can:

- View tickets assigned to them
- Update assigned tickets
- Update ticket status
- Add comments

An agent cannot access tickets that are not assigned to them.

### ADMIN

Administrators have broader access and can:

- View all tickets
- Update tickets
- Delete tickets
- Assign tickets to agents
- Manage users

---

## 🎫 Ticket Lifecycle

Tickets follow a controlled workflow:

```text
OPEN
  ↓
IN_PROGRESS
  ↓
RESOLVED
  ↓
CLOSED
```

The application prevents invalid status transitions.

For example:

```text
OPEN → RESOLVED
```

is not allowed directly.

---

## 🔐 Authentication Flow

The application uses JWT for authentication.

### Login

```text
User
  |
  | Login
  ↓
Auth Controller
  |
  ↓
Validate credentials
  |
  ↓
Generate JWT
  |
  ↓
Return token
```

### Subsequent Requests

The client sends the JWT in the `Authorization` header:

```text
Authorization: Bearer <JWT>
```

The request then follows this flow:

```text
Client
  |
  ↓
JWT Filter
  |
  ↓
Extract User ID + Role
  |
  ↓
Security Context
  |
  ↓
Controller
  |
  ↓
Service
```

The JWT contains:

- User ID
- User role
- Issued time
- Expiration time

---

## 🔒 Authorization

Authorization is enforced based on the authenticated user's role and ownership.

### Customer Access

```text
Customer
   |
   ├── Own ticket → ALLOWED
   |
   └── Another customer's ticket → DENIED
```

### Agent Access

```text
Agent
   |
   ├── Assigned ticket → ALLOWED
   |
   └── Unassigned / another agent's ticket → DENIED
```

Administrators can access tickets across the system.

Unauthorized requests return:

```text
HTTP 403 Forbidden
```

---

## 🗄️ Database Design

The main entities are:

```text
User
  |
  | 1
  |
  | *
Ticket
  |
  | 1
  |
  | *
Comment
```

A `Ticket` is associated with:

- The user who created it
- The agent assigned to it

A `Comment` is associated with:

- A ticket
- The user who created the comment

### User

Stores:

- ID
- Name
- Email
- Password
- Role

### Ticket

Stores:

- ID
- Title
- Description
- Priority
- Status
- Created By
- Assigned To

### Comment

Stores:

- ID
- Message
- Ticket
- User

A ticket can have multiple comments.

---

## 🌐 API Endpoints

### Authentication

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/login` | Login and receive JWT |

### Users

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/users` | Get users |
| GET | `/api/users/{id}` | Get user by ID |
| POST | `/api/users` | Create user |
| PUT | `/api/users/{id}` | Update user |

### Tickets

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/tickets` | Create ticket |
| GET | `/api/tickets` | Get tickets based on user role |
| GET | `/api/tickets/{id}` | Get ticket by ID |
| PUT | `/api/tickets/{id}` | Update ticket |
| DELETE | `/api/tickets/{id}` | Delete ticket |

### Ticket Operations

| Method | Endpoint | Description |
|--------|----------|-------------|
| PUT | `/api/tickets/{id}/assign` | Assign ticket to an agent |
| PUT | `/api/tickets/{id}/status` | Update ticket status |

### Pagination & Filtering

Get paginated tickets:

```http
GET /api/tickets/paged?page=0&size=20
```

Filter by status:

```http
GET /api/tickets/paged?status=OPEN&page=0&size=20
```

Filter by priority:

```http
GET /api/tickets/paged?priority=HIGH&page=0&size=20
```

Filter by both status and priority:

```http
GET /api/tickets/paged?status=OPEN&priority=HIGH&page=0&size=20
```

The results are also restricted according to the authenticated user's role.

---

## 💬 Comments

Users can add comments to tickets.

### Endpoint

```http
POST /api/tickets/{ticketId}/comments
```

### Request

```json
{
  "message": "The printer is working now."
}
```

The authenticated user is automatically associated with the comment.

---

## ⚠️ Exception Handling

The application uses centralized exception handling through a global exception handler.

Examples include:

- Ticket not found
- User not found
- Invalid login
- Duplicate email
- Invalid ticket status
- Invalid ticket assignment
- Validation errors
- Unauthorized access

Example response:

```json
{
  "message": "Ticket not found with id: 100"
}
```

---

## ✅ Input Validation

Request DTOs use Jakarta Bean Validation.

For example:

```java
@NotBlank
private String title;
```

This prevents invalid requests such as an empty ticket title.

---

## 🔑 Password Security

Passwords are not stored as plain text.

The application uses BCrypt for password hashing:

```text
Plain Password
      ↓
    BCrypt
      ↓
Hashed Password
      ↓
    MySQL
```

---

## 📁 Project Structure

```text
src/main/java/com/example/helpdesk_backend/

├── controller/
│   ├── AuthController.java
│   ├── UserController.java
│   ├── TicketController.java
│   └── CommentController.java
│
├── service/
│   ├── TicketService.java
│   ├── UserService.java
│   └── CommentService.java
│
├── repository/
│   ├── TicketRepository.java
│   ├── UserRepository.java
│   └── CommentRepository.java
│
├── entity/
│   ├── User.java
│   ├── Ticket.java
│   └── Comment.java
│
├── dto/
│   ├── LoginRequest.java
│   ├── RegisterRequest.java
│   ├── TicketRequest.java
│   ├── TicketResponse.java
│   ├── TicketUpdateRequest.java
│   └── CommentRequest.java
│
├── security/
│   ├── SecurityConfig.java
│   ├── JwtFilter.java
│   └── JwtService.java
│
└── exception/
    └── GlobalExceptionHandler.java
```

---

## ⚙️ Setup & Installation

### 1. Clone the repository

```bash
git clone <your-github-repository-url>
```

### 2. Create the MySQL database

```sql
CREATE DATABASE helpdesk_db;
```

### 3. Configure the database

The application uses an environment variable for the database password.

In `application.properties`:

```properties
spring.application.name=helpdesk-backend

spring.datasource.url=jdbc:mysql://localhost:3306/helpdesk_db
spring.datasource.username=root
spring.datasource.password=${DB_PASSWORD}

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
```

Set the `DB_PASSWORD` environment variable on your system.

### 4. Run the application

Using Maven:

```bash
./mvnw spring-boot:run
```

On Windows:

```bash
mvnw.cmd spring-boot:run
```

The application will start on:

```text
http://localhost:8080
```

---

## 🧪 Testing

The APIs were tested using Postman.

Important scenarios tested include:

- Successful login
- JWT authentication
- Customer accessing own ticket
- Customer attempting to access another customer's ticket
- Agent accessing assigned ticket
- Agent attempting to access an unassigned ticket
- Admin accessing tickets
- Ticket creation
- Ticket updates
- Ticket assignment
- Status transitions
- Pagination
- Status filtering
- Priority filtering
- Combined filtering
- Validation errors
- Exception handling

---

## 🔄 Example End-to-End Flow

```text
1. User logs in
       ↓
2. Server validates credentials
       ↓
3. JWT token is generated
       ↓
4. Client sends JWT with requests
       ↓
5. Customer creates a ticket
       ↓
6. Admin assigns the ticket to an agent
       ↓
7. Agent views the assigned ticket
       ↓
8. Agent changes the ticket status
       ↓
OPEN → IN_PROGRESS → RESOLVED
       ↓
9. Ticket is closed
```

---

## 🎯 Key Backend Concepts Demonstrated

This project demonstrates practical backend development concepts including:

- REST API development
- Layered architecture
- Dependency Injection
- DTO pattern
- JPA/Hibernate
- Entity relationships
- Spring Data JPA
- Authentication
- JWT
- Role-based authorization
- Password hashing
- Input validation
- Exception handling
- Pagination
- Filtering
- Business logic
- HTTP status codes
- MySQL database integration

---

## 🚀 Future Improvements

Possible future improvements include:

- Email notifications
- File attachments
- Ticket search
- Audit logs
- Automated tests
- Docker deployment
- Production deployment

---

## 👨‍💻 Author

**Harshad Rawat**

Java | Spring Boot | Backend Development | DSA