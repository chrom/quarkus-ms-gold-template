package org.acme.security;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Minimal security headers baseline for stage/prod.
 *
 * Owner for "edge-grade" hardening (rate limiting, bot/DoS, full CORS) remains the gateway.
 * This filter ensures the service is not "naked" if exposed without a gateway.
 */
@Provider
@Priority(Priorities.HEADER_DECORATOR)
@ApplicationScoped
public class SecurityHeadersFilter implements ContainerResponseFilter {

    @ConfigProperty(name = "template.security.headers.enabled", defaultValue = "false")
    boolean enabled;

    @Override
    public void filter(
            ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        if (!enabled) {
            return;
        }

        // Conservative defaults; adjust per product needs.
        responseContext.getHeaders().putSingle("X-Content-Type-Options", "nosniff");
        responseContext.getHeaders().putSingle("X-Frame-Options", "DENY");
        responseContext.getHeaders().putSingle("Referrer-Policy", "no-referrer");
        responseContext.getHeaders().putSingle("Permissions-Policy", "geolocation=(), microphone=(), camera=()");

        // HSTS must be set only when serving HTTPS end-to-end.
        // In Kubernetes this is typically terminated at the gateway/ingress.
        // responseContext.getHeaders().putSingle("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
    }
}

