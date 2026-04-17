package org.acme.rest.filter;

import java.io.IOException;
import java.util.UUID;

import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

@Provider
@Priority(Priorities.AUTHENTICATION)
public class RequestIdFilter implements ContainerRequestFilter, ContainerResponseFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";

    private static final Logger LOG = Logger.getLogger(RequestIdFilter.class);

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String requestId = requestContext.getHeaderString(HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = UUID.randomUUID().toString();
        }

        // Ensure access-log can read it from request headers too
        requestContext.getHeaders().putSingle(HEADER, requestId);

        MDC.put(MDC_KEY, requestId);
        requestContext.setProperty(MDC_KEY, requestId);
        requestContext.setProperty("startNanos", System.nanoTime());

        if (LOG.isInfoEnabled()) {
            LOG.infov("request.start method={0} path={1}", requestContext.getMethod(), requestContext.getUriInfo().getPath());
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        String requestId = (String) requestContext.getProperty(MDC_KEY);
        if (requestId != null) {
            responseContext.getHeaders().putSingle(HEADER, requestId);
        }

        Long startNanos = (Long) requestContext.getProperty("startNanos");
        if (startNanos != null && LOG.isInfoEnabled()) {
            long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
            LOG.infov(
                    "request.end method={0} path={1} status={2} durationMs={3}",
                    requestContext.getMethod(),
                    requestContext.getUriInfo().getPath(),
                    responseContext.getStatus(),
                    durationMs);
        }

        MDC.remove(MDC_KEY);
    }
}
