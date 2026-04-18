package org.acme.openapi;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.eclipse.microprofile.openapi.OASFactory;
import org.eclipse.microprofile.openapi.OASFilter;
import org.eclipse.microprofile.openapi.models.Components;
import org.eclipse.microprofile.openapi.models.OpenAPI;
import org.eclipse.microprofile.openapi.models.Operation;
import org.eclipse.microprofile.openapi.models.PathItem;
import org.eclipse.microprofile.openapi.models.media.Content;
import org.eclipse.microprofile.openapi.models.media.MediaType;
import org.eclipse.microprofile.openapi.models.media.Schema;
import org.eclipse.microprofile.openapi.models.responses.APIResponse;
import org.eclipse.microprofile.openapi.models.responses.APIResponses;

import io.quarkus.smallrye.openapi.OpenApiFilter;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Normalises every 4xx/5xx response in the generated OpenAPI document so that it:
 * <ol>
 *   <li>Declares {@code application/problem+json} as its content type, and</li>
 *   <li>References the shared {@code #/components/schemas/Problem} schema.</li>
 * </ol>
 *
 * <p>Quarkus' SmallRye OpenAPI extension auto-generates responses from JAX-RS signatures /
 * {@code @APIResponse} annotations, but it has no opinion about <em>error</em> content types —
 * which means individual resource methods would have to repeat the same problem schema everywhere.
 * Centralising the concern in an {@link OASFilter} guarantees the contract is uniform and cannot
 * drift from the implementation (see {@link org.acme.rest.error.ProblemDetail} and ADR 0012).
 *
 * <p>The filter runs at {@code BUILD}, {@code RUNTIME_STARTUP} and {@code RUNTIME_PER_REQUEST} so
 * that:
 * <ul>
 *   <li>{@code quarkus.smallrye-openapi.store-schema-directory} (used by the openapi-sync gate,
 *       see Makefile target {@code openapi-check-sync}) gets the same document as the runtime
 *       {@code /q/openapi}.</li>
 *   <li>Integration tests hitting the live endpoint see the normalised schema.</li>
 * </ul>
 */
@OpenApiFilter(stages = {
        OpenApiFilter.RunStage.BUILD,
        OpenApiFilter.RunStage.RUNTIME_STARTUP,
        OpenApiFilter.RunStage.RUNTIME_PER_REQUEST
})
@ApplicationScoped
public class ProblemDetailsOASFilter implements OASFilter {

    /** Matches {@link org.acme.rest.error.ProblemDetail#MEDIA_TYPE}. */
    private static final String PROBLEM_JSON = "application/problem+json";

    /** Name under {@code #/components/schemas/}. Matches {@code @Schema(name = "Problem")}. */
    private static final String PROBLEM_SCHEMA_NAME = "Problem";

    private static final String PROBLEM_SCHEMA_REF = "#/components/schemas/" + PROBLEM_SCHEMA_NAME;

    @Override
    public void filterOpenAPI(OpenAPI openAPI) {
        if (openAPI == null) {
            return;
        }
        ensureProblemSchemaRegistered(openAPI);
        if (openAPI.getPaths() == null || openAPI.getPaths().getPathItems() == null) {
            return;
        }
        for (PathItem item : openAPI.getPaths().getPathItems().values()) {
            visitPathItem(item);
        }
    }

    /**
     * Registers a placeholder {@code Problem} object schema if the annotation-driven scanner
     * did not pick it up. In practice {@link org.acme.rest.error.ProblemDetail} is always scanned
     * (every mapper returns it as its entity), but we defend against filter-order surprises — a
     * missing schema turns {@code $ref} into a dangling pointer that breaks Spectral + clients.
     */
    private static void ensureProblemSchemaRegistered(OpenAPI openAPI) {
        Components components = openAPI.getComponents();
        if (components == null) {
            components = OASFactory.createComponents();
            openAPI.setComponents(components);
        }
        Map<String, Schema> schemas = components.getSchemas();
        if (schemas != null && schemas.containsKey(PROBLEM_SCHEMA_NAME)) {
            return;
        }
        Schema stub = OASFactory.createSchema()
                .addType(Schema.SchemaType.OBJECT)
                .description("RFC 7807 application/problem+json error body.");
        components.addSchema(PROBLEM_SCHEMA_NAME, stub);
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
                .forEach(ProblemDetailsOASFilter::visitOperation);
    }

    private static void visitOperation(Operation op) {
        APIResponses responses = op.getResponses();
        if (responses == null) {
            return;
        }
        Map<String, APIResponse> map = responses.getAPIResponses();
        if (map == null) {
            return;
        }
        for (Map.Entry<String, APIResponse> entry : map.entrySet()) {
            String statusCode = entry.getKey();
            APIResponse response = entry.getValue();
            if (!isClientOrServerError(statusCode) || response == null) {
                continue;
            }
            applyProblemContent(response);
        }
    }

    /**
     * Returns {@code true} for status codes describing an error. Matches OpenAPI 3's code forms:
     * <ul>
     *   <li>Explicit codes: {@code 400}, {@code 404}, {@code 503}, …</li>
     *   <li>Range wildcards: {@code 4XX}, {@code 5XX} (case-insensitive per spec).</li>
     * </ul>
     * Anything else (including {@code default} and 1xx/2xx/3xx) is left untouched.
     */
    private static boolean isClientOrServerError(String statusCode) {
        if (statusCode == null || statusCode.isEmpty()) {
            return false;
        }
        char first = statusCode.charAt(0);
        return first == '4' || first == '5';
    }

    /**
     * Replaces the content map of the response with a single {@code application/problem+json}
     * entry referencing the shared {@code Problem} schema.
     *
     * <p>We intentionally overwrite any pre-existing {@code application/json} entry: the whole
     * point of the filter is that error responses <strong>must</strong> advertise the problem
     * media type, so competing entries would defeat both content negotiation and Spectral's lint.
     * If a specific endpoint needs a different error payload it should declare an explicit
     * non-4xx/5xx status code or introduce a dedicated error schema and register it here.
     */
    private static void applyProblemContent(APIResponse response) {
        Schema refSchema = OASFactory.createSchema().ref(PROBLEM_SCHEMA_REF);
        MediaType problemMedia = OASFactory.createMediaType().schema(refSchema);
        Content content = OASFactory.createContent();
        content.addMediaType(PROBLEM_JSON, problemMedia);
        response.setContent(content);
    }
}
