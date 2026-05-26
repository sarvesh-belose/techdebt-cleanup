package com.enterprise.techdebt.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI techdebtOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("TechDebt Cleanup Platform API")
                        .version("0.1.0-SNAPSHOT")
                        .description("Backend API for the enterprise tech-debt automation platform. "
                                + "Routes are grouped by domain: projects, scan-sources, scans, issues, "
                                + "fix-runs, notifications, analytics, system, compliance.")
                        .contact(new Contact().name("Platform team").email("techdebt-platform@enterprise.local"))
                        .license(new License().name("Internal")))
                .components(new Components()
                        .addSecuritySchemes("bearer-auth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Azure AD JWT bearer token (production)")))
                .addSecurityItem(new SecurityRequirement().addList("bearer-auth"));
    }
}
