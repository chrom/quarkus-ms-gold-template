package org.acme.catalog.adapter.in.rest;

import org.acme.catalog.domain.exception.CatalogNotFoundException;
import org.acme.rest.error.ProblemDetails;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps catalog domain "not found" failures to RFC 7807 {@code application/problem+json} (ADR 0012).
 * Keeps JAX-RS types out of the application layer.
 */
@Provider
public class CatalogNotFoundExceptionMapper implements ExceptionMapper<CatalogNotFoundException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(CatalogNotFoundException exception) {
        return ProblemDetails.response(Status.NOT_FOUND, exception.getMessage(), uriInfo);
    }
}
