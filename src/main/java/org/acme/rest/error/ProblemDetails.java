package org.acme.rest.error;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.jboss.logging.MDC;

import org.acme.rest.filter.RequestIdFilter;

import jakarta.validation.ConstraintViolation;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriInfo;

/**
 * Static factory for building {@link ProblemDetail} responses.
 *
 * <p>Centralising construction is deliberate: every mapper (unhandled / web-application /
 * constraint-violation / catalog-not-found) goes through the same path so the wire contract
 * (headers, status coupling, requestId extraction, timestamp source) can never drift between
 * mappers. See ADR 0012.
 */
public final class ProblemDetails {

    private ProblemDetails() {
        /* utility class */
    }

    /**
     * Build a {@link Response} with status and {@code application/problem+json} body.
     *
     * @param status   HTTP status code for the response.
     * @param detail   human-readable explanation specific to this occurrence (may be {@code null}).
     * @param uriInfo  JAX-RS UriInfo, used to populate the {@code instance} field (request path). May be {@code null}.
     * @return a ready-to-return JAX-RS response.
     */
    public static Response response(Status status, String detail, UriInfo uriInfo) {
        return response(status, detail, uriInfo, null);
    }

    /**
     * Same as {@link #response(Status, String, UriInfo)} but attaches per-field validation errors.
     * Intended for {@link ConstraintViolationExceptionMapper}.
     */
    public static Response response(
            Status status, String detail, UriInfo uriInfo, List<ProblemDetail.FieldError> errors) {
        ProblemDetail body = new ProblemDetail(
                ProblemDetail.DEFAULT_TYPE,
                status.getReasonPhrase(),
                status.getStatusCode(),
                detail,
                instanceOf(uriInfo),
                currentRequestId(),
                Instant.now(),
                errors);

        return Response.status(status)
                .type(MediaType.valueOf(ProblemDetail.MEDIA_TYPE))
                .entity(body)
                .build();
    }

    /**
     * Translate a {@link Set} of bean-validation violations into {@link ProblemDetail.FieldError}s.
     *
     * <p>Uses the last node of the property path as the {@code field} name — the common case for
     * JAX-RS parameters and top-level JSON fields. Nested paths are intentionally collapsed because
     * our request DTOs are shallow; revisit if we introduce complex object graphs.
     */
    public static List<ProblemDetail.FieldError> fieldErrors(Set<? extends ConstraintViolation<?>> violations) {
        List<ProblemDetail.FieldError> out = new ArrayList<>(violations.size());
        for (ConstraintViolation<?> v : violations) {
            out.add(new ProblemDetail.FieldError(lastNode(v), v.getMessage()));
        }
        return out;
    }

    private static String lastNode(ConstraintViolation<?> violation) {
        String full = violation.getPropertyPath().toString();
        int dot = full.lastIndexOf('.');
        return dot >= 0 ? full.substring(dot + 1) : full;
    }

    private static URI instanceOf(UriInfo uriInfo) {
        if (uriInfo == null) {
            return null;
        }
        String path = uriInfo.getPath();
        if (path == null) {
            return null;
        }
        return URI.create(path.startsWith("/") ? path : "/" + path);
    }

    private static String currentRequestId() {
        Object value = MDC.get(RequestIdFilter.MDC_KEY);
        return value == null ? null : value.toString();
    }
}
