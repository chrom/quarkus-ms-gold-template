package org.acme.rest;

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.acme.config.AppConfig;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/config")
@Tag(name = "Config", description = "Non-secret configuration and diagnostics (no secrets).")
public class ConfigResource {

    private final String greetingMessage;
    private final boolean http2Enabled;
    private final int httpPort;
    private final String datasourceDbKind;
    private final AppConfig appConfig;

    @Inject
    public ConfigResource(
            @ConfigProperty(name = "greeting.message", defaultValue = "Hello from Quarkus REST") String greetingMessage,
            @ConfigProperty(name = "quarkus.http.http2", defaultValue = "false") boolean http2Enabled,
            @ConfigProperty(name = "quarkus.http.port", defaultValue = "8080") int httpPort,
            @ConfigProperty(name = "quarkus.datasource.db-kind", defaultValue = "") String datasourceDbKind,
            AppConfig appConfig) {
        this.greetingMessage = greetingMessage;
        this.http2Enabled = http2Enabled;
        this.httpPort = httpPort;
        this.datasourceDbKind = datasourceDbKind;
        this.appConfig = appConfig;
    }

    /**
     * Public (safe) configuration slice for diagnostics.
     * Do not add secrets (passwords/tokens/connection strings) here.
     */
    @GET
    @Path("/public")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(
            operationId = "getPublicConfig",
            summary = "Public configuration slice",
            description =
                    "Returns a JSON map of safe, non-secret settings (ports, app name, feature flags). "
                            + "Do not add secrets to this response.")
    public Map<String, Object> publicConfig() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("greeting.message", greetingMessage);
        cfg.put("quarkus.http.http2", http2Enabled);
        cfg.put("quarkus.http.port", httpPort);
        cfg.put("quarkus.datasource.db-kind", datasourceDbKind);
        
        // Adding generated configuration from AppConfig
        cfg.put("app.name", appConfig.name());
        cfg.put("app.version", appConfig.version());
        cfg.put("app.features.experimental", appConfig.features().experimental());
        cfg.put("app.features.enabled-list", appConfig.features().enabledList());
        
        return cfg;
    }

    @GET
    @Path("/greeting-message")
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(
            operationId = "getGreetingMessage",
            summary = "Configured greeting text",
            description = "Returns the raw `greeting.message` value as plain text.")
    public String greetingMessage() {
        return greetingMessage;
    }
}

