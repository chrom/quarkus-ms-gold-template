package org.acme.catalog.adapter.in.rest;

import java.util.Map;

import org.acme.catalog.domain.exception.CatalogNotFoundException;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps catalog domain "not found" failures to HTTP 404 — keeps JAX-RS types out of the application layer.
 */
@Provider
public class CatalogNotFoundExceptionMapper implements ExceptionMapper<CatalogNotFoundException> {

    @Override
    public Response toResponse(CatalogNotFoundException exception) {
        return Response.status(Response.Status.NOT_FOUND)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of("error", exception.getMessage()))
                .build();
    }
}
