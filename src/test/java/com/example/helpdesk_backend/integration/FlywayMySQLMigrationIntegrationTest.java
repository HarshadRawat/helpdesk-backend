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

        Integer ticketCount = jdbcTemplate.queryForObject("SELECT count(*) FROM tickets", Integer.class);
        assertNotNull(ticketCount);
    }
}
