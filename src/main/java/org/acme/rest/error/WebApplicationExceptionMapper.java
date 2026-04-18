package org.acme.rest.error;

import org.jboss.logging.Logger;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps all JAX-RS {@link WebApplicationException}s (including {@code NotFoundException},
 * {@code NotAllowedException}, {@code BadRequestException}, 401/403 from security, …) to
 * {@link ProblemDetail} — preserving the original status and detail message.
 *
 * <p>This handler replaces both the legacy {@code NotFoundExceptionMapper} and the custom
 * {@code GlobalExceptionMapper} so there is exactly one path that produces a 4xx JAX-RS error
 * body. Status 5xx subtypes of {@code WebApplicationException} (e.g. {@code InternalServerErrorException}
 * explicitly thrown by user code) are mapped identically; truly unexpected {@link Throwable}s are
 * still funnelled through {@link UnhandledExceptionMapper}.
 */
@Provider
public class WebApplicationExceptionMapper implements ExceptionMapper<WebApplicationException> {

    private static final Logger LOG = Logger.getLogger(WebApplicationExceptionMapper.class);

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(WebApplicationException exception) {
        Response original = exception.getResponse();
        int code = original == null ? 500 : original.getStatus();
        Status status = Status.fromStatusCode(code);
        if (status == null) {
            status = Status.INTERNAL_SERVER_ERROR;
        }

        // Keep the framework-provided message (e.g. "HTTP 404 Not Found" or the explicit
        // message passed to `throw new NotFoundException("Product 42 not found")`). We do NOT
        // fall back to `exception.getMessage()` alone because for several 4xx subclasses it
        // contains the raw HTTP status line, which is not useful as `detail`.
        String detail = exception.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = status.getReasonPhrase();
        }

        // 4xx → DEBUG (client fault, high volume under attack; don't flood the log).
        // 5xx → WARN/ERROR (server fault, may already be logged at source — keep WARN here
        // so we don't double-log, but still surface it for on-call dashboards).
        if (code >= 500) {
            LOG.warnf(exception, "WebApplicationException %d: %s", code, detail);
        } else if (LOG.isDebugEnabled()) {
            LOG.debugf("WebApplicationException %d: %s", code, detail);
        }

        return ProblemDetails.response(status, detail, uriInfo);
    }
}
