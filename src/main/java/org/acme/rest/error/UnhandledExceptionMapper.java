package org.acme.rest.error;

import java.util.Map;

import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class UnhandledExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(UnhandledExceptionMapper.class);

    @Override
    public Response toResponse(Throwable exception) {
        String requestId = (String) MDC.get("requestId");
        LOG.errorf(exception, "Unhandled exception. requestId=%s", requestId);

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(Map.of(
                        "error", "Internal Server Error",
                        "requestId", requestId))
                .build();
    }
}
