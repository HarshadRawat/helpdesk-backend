package com.example.helpdesk_backend.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Testcontainers(disabledWithoutDocker = true)
class FlywayMySQLMigrationIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("helpdesk_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Autowired(required = false)
    private Flyway flyway;

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @Test
    void testFlywayMigrationsAppliedOnRealMySQL() {
        if (!mysql.isRunning()) {
            // Skipped when Docker daemon is not active
            return;
        }

        assertNotNull(flyway, "Flyway bean should be present");
        MigrationInfo[] applied = flyway.info().applied();
        assertTrue(applied.length >= 6, "Expected at least 6 migrations applied (V1 through V6)");

        // Verify MySQL specific columns and tables
        Integer userCount = jdbcTemplate.queryForObject("SELECT count(*) FROM users", Integer.class);
        assertNotNull(userCount);

        Integer ticketCount = jdbcTemplate.queryForObject("SELECT count(*) FROM ticket", Integer.class);
        assertNotNull(ticketCount);

        // Verify full MySQL schema lifecycle: insert user, insert ticket with FK and timestamps, read back
        jdbcTemplate.update(
                "INSERT INTO users (name, email, password, role, active) VALUES (?, ?, ?, ?, ?)",
                "MySQL Admin", "mysql_admin@helpdesk.com", "HashedPassword123!", "ADMIN", true
        );
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = 'mysql_admin@helpdesk.com'", Long.class
        );
        assertNotNull(userId);

        jdbcTemplate.update(
                "INSERT INTO ticket (title, description, priority, status, created_by, version, sla_due_at, sla_breached, deleted) " +
                        "VALUES (?, ?, ?, ?, ?, ?, NOW(), ?, ?)",
                "MySQL Production Readiness Ticket", "Testing MySQL schema and triggers", "URGENT", "OPEN", userId, 0L, false, false
        );

        java.util.Map<String, Object> ticketRow = jdbcTemplate.queryForMap(
                "SELECT id, title, priority, status, version, sla_breached, deleted, created_at, updated_at " +
                        "FROM ticket WHERE created_by = ?", userId
        );

        assertEquals("MySQL Production Readiness Ticket", ticketRow.get("title"));
        assertEquals("URGENT", ticketRow.get("priority"));
        assertEquals("OPEN", ticketRow.get("status"));
        assertEquals(0L, ((Number) ticketRow.get("version")).longValue());
        assertEquals(false, ticketRow.get("sla_breached"));
        assertEquals(false, ticketRow.get("deleted"));
        assertNotNull(ticketRow.get("created_at"));
        assertNotNull(ticketRow.get("updated_at"));
    }
}
