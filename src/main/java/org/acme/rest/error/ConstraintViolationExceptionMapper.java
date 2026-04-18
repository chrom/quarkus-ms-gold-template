package org.acme.rest.error;

import java.util.List;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps bean-validation failures to a 400 {@link ProblemDetail}, enriched with per-field errors
 * under the {@code errors} extension member.
 *
 * <p>Quarkus registers a default mapper for {@link ConstraintViolationException} that returns a
 * proprietary JSON shape. Declaring our own {@link Provider} wins because providers registered
 * by user code take precedence over built-ins. See
 * <a href="https://quarkus.io/guides/rest#exception-mapping">Quarkus REST — exception mapping</a>.
 */
@Provider
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Context
    UriInfo uriInfo;

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        List<ProblemDetail.FieldError> errors = ProblemDetails.fieldErrors(exception.getConstraintViolations());
        return ProblemDetails.response(Status.BAD_REQUEST, "Validation failed", uriInfo, errors);
    }
}
