package com.catalogix.checkout.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

// Docs available at /swagger-ui.html once the app is running.
@OpenAPIDefinition(info = @Info(
        title = "Catalogix — checkout-svc",
        description = "Places, pays for and cancels orders; prices items via catalog-svc and reserves or releases stock via inventory-svc.",
        version = "v1"))
@SecurityScheme(
        name = "bearerAuth",
        type = io.swagger.v3.oas.annotations.enums.SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        in = SecuritySchemeIn.HEADER)
@Configuration
public class OpenApiConfig {
}
