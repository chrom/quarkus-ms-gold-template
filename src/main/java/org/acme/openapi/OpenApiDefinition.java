package org.acme.openapi;

import org.eclipse.microprofile.openapi.annotations.Components;
import org.eclipse.microprofile.openapi.annotations.OpenAPIDefinition;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.info.Contact;
import org.eclipse.microprofile.openapi.annotations.info.Info;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Global OpenAPI {@code info} and tag registry. {@link OpenAPIDefinition} requires a non-default
 * {@code info} element at compile time (MicroProfile OpenAPI).
 */
@OpenAPIDefinition(
        info =
                @Info(
                        title = "Quarkus MS Gold Template API",
                        version = "1.0.0-SNAPSHOT",
                        description =
                                "Reference REST API for the Gold Template: diagnostics, configuration "
                                        + "endpoints, and the catalog bounded context (products and categories).",
                        contact = @Contact(name = "Platform / template maintainers", email = "platform@example.com")),
        components =
                @Components(
                        securitySchemes = {
                            @SecurityScheme(
                                    securitySchemeName = "oidc-jwt",
                                    type = SecuritySchemeType.HTTP,
                                    scheme = "bearer",
                                    bearerFormat = "JWT",
                                    description =
                                            "OIDC access token (Keycloak realm roles). `/api/secured` exists only with "
                                                    + "profile `secured` — see docs/security/oidc-secured-profile.md.")
                        }),
        tags = {
            @Tag(name = "Greeting", description = "Plain-text hello and goodbye endpoints."),
            @Tag(name = "Config", description = "Non-secret configuration and diagnostics (no secrets)."),
            @Tag(name = "Categories", description = "Product categories (read-only)."),
            @Tag(name = "Products", description = "Product CRUD and recommendations."),
            @Tag(
                    name = "Secured (OIDC)",
                    description =
                            "JWT bearer validation (optional `secured` profile). Not present in default OpenAPI export.")
        })
public class OpenApiDefinition {}
