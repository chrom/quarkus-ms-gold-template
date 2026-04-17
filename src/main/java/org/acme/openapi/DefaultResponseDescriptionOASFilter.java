package org.acme.openapi;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.eclipse.microprofile.openapi.OASFilter;
import io.quarkus.smallrye.openapi.OpenApiFilter;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.responses.APIResponse;
import org.eclipse.microprofile.openapi.models.responses.APIResponses;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Ensures every response has a non-empty description (OpenAPI 3 / strict validators).
 * Covers Panache REST Data and any operation where descriptions are omitted.
 * {@code BUILD} is required so {@code store-schema-directory} output matches runtime.
 */
@OpenApiFilter(stages = {
        OpenApiFilter.RunStage.BUILD,
        OpenApiFilter.RunStage.RUNTIME_STARTUP,
        OpenApiFilter.RunStage.RUNTIME_PER_REQUEST
})
@ApplicationScoped
public class DefaultResponseDescriptionOASFilter implements OASFilter {

    private static final String FALLBACK = "OK";

    @Override
    public void filterOpenAPI(OpenAPI openAPI) {
        if (openAPI == null || openAPI.getPaths() == null || openAPI.getPaths().getPathItems() == null) {
            return;
        }
        for (PathItem item : openAPI.getPaths().getPathItems().values()) {
            visitPathItem(item);
        }
    }

    private static void visitPathItem(PathItem item) {
        if (item == null) {
            return;
        }
        Stream.of(
                item.getGET(),
                item.getPUT(),
                item.getPOST(),
                item.getDELETE(),
                item.getOPTIONS(),
                item.getHEAD(),
                item.getPATCH(),
                item.getTRACE())
                .filter(Objects::nonNull)
                .forEach(DefaultResponseDescriptionOASFilter::visitOperation);
    }

    private static void visitOperation(Operation op) {
        if (op == null) {
            return;
        }
        APIResponses responses = op.getResponses();
        if (responses == null) {
            return;
        }
        Map<String, APIResponse> map = responses.getAPIResponses();
        if (map != null) {
            for (APIResponse resp : map.values()) {
                ensureDescription(resp);
            }
        }
        ensureDescription(responses.getDefaultValue());
    }

    private static void ensureDescription(APIResponse resp) {
        if (resp == null) {
            return;
        }
        String desc = resp.getDescription();
        if (desc == null || desc.isBlank()) {
            resp.setDescription(FALLBACK);
        }
    }
}
