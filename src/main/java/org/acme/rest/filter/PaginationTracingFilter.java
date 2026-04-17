package org.acme.rest.filter;

import java.io.IOException;
import java.util.List;

import io.opentelemetry.api.trace.Span;

import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

/**
 * Adds pagination attributes (page, size) to the current HTTP span for GET requests with query parameters.
 * Covers both {@code /products} and the generated Panache REST Data {@code /categories}.
 */
@Provider
@Priority(Priorities.AUTHENTICATION + 100)
public class PaginationTracingFilter implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (!"GET".equalsIgnoreCase(requestContext.getMethod())) {
            return;
        }
        var params = requestContext.getUriInfo().getQueryParameters();
        List<String> page = params.get("page");
        List<String> size = params.get("size");
        if (page == null || page.isEmpty() || size == null || size.isEmpty()) {
            return;
        }
        Span span = Span.current();
        if (!span.isRecording()) {
            return;
        }
        try {
            span.setAttribute("pagination.page", Long.parseLong(page.getFirst()));
            span.setAttribute("pagination.size", Long.parseLong(size.getFirst()));
        } catch (NumberFormatException ignored) {
            // do not fail the request due to tracing
        }
    }
}
