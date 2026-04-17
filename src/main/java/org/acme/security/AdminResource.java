package org.acme.security;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * Minimal AuthZ example (ADR 0008 Phase B): protect a business operation with a role.
 *
 * In stage/prod, authentication is expected to be enforced by OIDC (JWT bearer) and roles are
 * mapped from Keycloak realm roles (realm_access/roles).
 */
@Path("/api/admin")
@Tag(name = "Admin", description = "Authorization example (role-protected endpoint).")
@SecurityRequirement(name = "oidc-jwt")
public class AdminResource {

    @GET
    @Path("/ping")
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("admin")
    @Operation(operationId = "adminPing", summary = "Admin ping", description = "Requires role 'admin'.")
    @APIResponse(responseCode = "200", description = "OK")
    @APIResponse(responseCode = "401", description = "Unauthenticated")
    @APIResponse(responseCode = "403", description = "Forbidden (missing role)")
    public PingResponse ping() {
        return new PingResponse("ok");
    }

    public record PingResponse(String status) {}
}

