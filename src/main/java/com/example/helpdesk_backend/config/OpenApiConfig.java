package com.example.helpdesk_backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String SECURITY_SCHEME_NAME = "BearerAuth";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("HelpDesk Enterprise Backend REST API")
                        .version("1.0.0")
                        .description("Production-grade IT Operations & Helpdesk Support Backend API built with Spring Boot 4. "
                                + "Features JWT authentication with refresh token rotation, role-based access control (RBAC), "
                                + "enterprise SLA escalation tracking, event-driven async notification pipeline, "
                                + "optimistic concurrency locking, multipart file attachments, and analytical caching.")
                        .contact(new Contact()
                                .name("HelpDesk Engineering Team")
                                .url("https://github.com/HarshadRawat/helpdesk-backend"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .name(SECURITY_SCHEME_NAME)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter your JWT access token (omit the 'Bearer ' prefix).")));
    }
}
