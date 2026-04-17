package org.acme.rest;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.Set;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Present only when the {@code secured} build profile is active (see {@code application.properties}
 * and {@code docs/security/oidc-secured-profile.md}). Validates Bearer JWTs via OIDC discovery.
 */
@IfBuildProfile("secured")
@Path("/api/secured")
@Tag(name = "Secured (OIDC)", description = "JWT bearer validation (optional secured profile).")
@SecurityRequirement(name = "oidc-jwt")
public class SecuredResource {

    @GET
    @Path("/me")
    @Produces(MediaType.APPLICATION_JSON)
    @Authenticated
    @Operation(
            operationId = "getSecuredMe",
            summary = "Current caller (JWT)",
            description =
                    "Returns subject and roles from the validated access token. Requires "
                            + "`Authorization: Bearer <access_token>`.")
    @APIResponse(
            responseCode = "200",
            description = "Caller identity",
            content =
                    @Content(
                            mediaType = MediaType.APPLICATION_JSON,
                            schema = @Schema(implementation = MeResponse.class)))
    @APIResponse(responseCode = "401", description = "Missing or invalid bearer token")
    public MeResponse me(SecurityIdentity identity) {
        return new MeResponse(
                identity.getPrincipal() != null ? identity.getPrincipal().getName() : null,
                identity.getRoles());
    }

    @Schema(description = "Resolved identity from the OIDC access token")
    public record MeResponse(String subject, Set<String> roles) {}
}
