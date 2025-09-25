package com.valarpirai.example.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger configuration for the Sample Sharded Application.
 * Provides comprehensive API documentation for the sharding demo.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Sample Sharded Application API")
                        .description("REST API demonstrating multi-tenant database sharding with Spring Boot. " +
                                   "This application showcases how to build scalable multi-tenant systems with " +
                                   "tenant-specific data isolation using database sharding.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Sharding Demo")
                                .email("demo@example.com")
                                .url("https://github.com/example/java-backend-dev"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Development server"),
                        new Server()
                                .url("https://api.example.com")
                                .description("Production server")))
                .addTagsItem(new io.swagger.v3.oas.models.tags.Tag()
                        .name("customers")
                        .description("Customer management operations with tenant-based sharding"))
                .addTagsItem(new io.swagger.v3.oas.models.tags.Tag()
                        .name("tenant-context")
                        .description("Tenant context management for multi-tenant operations"))
                .addTagsItem(new io.swagger.v3.oas.models.tags.Tag()
                        .name("monitoring")
                        .description("System monitoring and health check endpoints"));
    }
}