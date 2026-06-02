package com.example.bookserver.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger configuration. The {@code bearerAuth} scheme is declared
 * globally but NOT registered as a global security requirement: public
 * endpoints (e.g. {@code /api/auth/login}, {@code /api/auth/register},
 * {@code /api/auth/refresh}, {@code /actuator/health}) must remain unmarked.
 * Protected endpoints opt in explicitly via
 * {@code @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")}.
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("BookServerFull API")
                        .version("0.0.1")
                        .description("REST API for the BookServerFull library application."))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME_NAME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
