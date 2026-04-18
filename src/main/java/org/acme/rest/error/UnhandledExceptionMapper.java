package org.acme.rest.error;

import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Last-resort mapper for anything not handled by a more specific {@link ExceptionMapper}.
 *
 * <p>Always emits an RFC 7807 {@code application/problem+json} body (ADR 0012). Also logs the
 * root cause together with the current {@code requestId} so the response the client receives
 * can be correlated with server-side traces/logs.
 */
@Provider
public class UnhandledExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(UnhandledExceptionMapper.class);

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(Throwable exception) {
        Object requestId = MDC.get("requestId");
        LOG.errorf(exception, "Unhandled exception. requestId=%s", requestId);

        return ProblemDetails.response(Status.INTERNAL_SERVER_ERROR, "Internal Server Error", uriInfo);
    }
}
