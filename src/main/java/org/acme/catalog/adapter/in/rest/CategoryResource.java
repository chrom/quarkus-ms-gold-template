package org.acme.catalog.adapter.in.rest;

import java.util.List;

import org.acme.catalog.adapter.in.rest.dto.CategoryResponse;
import org.acme.catalog.application.service.CategoryApplicationService;
import org.acme.catalog.domain.ids.CategoryId;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/categories")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Categories", description = "Product categories (read)")
public class CategoryResource {

    private final CategoryApplicationService categoryApplicationService;
    private final CatalogRestMapper mapper;

    @Inject
    public CategoryResource(CategoryApplicationService categoryApplicationService, CatalogRestMapper mapper) {
        this.categoryApplicationService = categoryApplicationService;
        this.mapper = mapper;
    }

    @GET
    @Operation(
            operationId = "listCategories",
            summary = "List categories",
            description = "Returns all product categories sorted by name.")
    @APIResponse(
            responseCode = "200",
            description = "OK",
            content = @Content(schema = @Schema(implementation = CategoryResponse.class)))
    public List<CategoryResponse> list() {
        return categoryApplicationService.listAllOrderByName().stream().map(mapper::toApi).toList();
    }

    @GET
    @Path("/{id}")
    @Operation(
            operationId = "getCategoryById",
            summary = "Category by id",
            description = "Returns a single category by numeric id, or 404 if not found.")
    @APIResponses({
        @APIResponse(
                responseCode = "200",
                description = "Found",
                content = @Content(schema = @Schema(implementation = CategoryResponse.class))),
        @APIResponse(responseCode = "404", description = "Not found")
    })
    public CategoryResponse get(@PathParam("id") Long id) {
        return mapper.toApi(categoryApplicationService.getById(new CategoryId(id)));
    }
}
