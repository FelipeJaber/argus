package br.com.argus.config;

import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;

/** Swagger UI em {@code /swagger-ui.html}. Use o botão "Authorize" com o accessToken do login. */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Argus API",
                version = "v1",
                description = "Governança de agentes de inteligência artificial: catálogo, homologação, "
                        + "políticas de uso e auditoria."),
        security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig {
}
