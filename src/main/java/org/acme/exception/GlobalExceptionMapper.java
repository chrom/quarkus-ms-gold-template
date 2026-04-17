package org.acme.exception;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.time.LocalDateTime;

/**
 * The @Provider annotation registers this class in JAX-RS as an interceptor component.
 * ExceptionMapper<Exception> means that all exceptions for which there is no 
 * more specific handler will fall here.
 */
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Exception> {

    // Request context (specifically, the path) is automatically injected
    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(Exception exception) {
        // Base values for unknown errors
        int statusCode = 500;
        String errorPhrase = "Internal Server Error";

        // If someone (or Quarkus) threw a standard WebApplicationException (e.g. code 404, 400)
        if (exception instanceof WebApplicationException webAppException) {
            statusCode = webAppException.getResponse().getStatus();
            errorPhrase = webAppException.getResponse().getStatusInfo().getReasonPhrase();
        }

        // Form our pretty error object
        ErrorResponse errorResponse = new ErrorResponse(
                statusCode,
                errorPhrase,
                exception.getMessage() != null ? exception.getMessage() : "Unknown error occurred",
                uriInfo != null ? uriInfo.getPath() : "Unknown path",
                LocalDateTime.now()
        );

        // Return the wrapped response in JSON format
        return Response.status(statusCode)
                .entity(errorResponse)
                .build();
    }
}
