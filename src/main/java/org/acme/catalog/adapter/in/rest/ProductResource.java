package org.acme.catalog.adapter.in.rest;

import java.net.URI;
import java.util.List;

import org.acme.catalog.adapter.in.rest.dto.ProductResponse;
import org.acme.catalog.adapter.in.rest.request.ProductRequest;
import org.acme.catalog.application.port.out.RecommendationPort;
import org.acme.catalog.application.service.ProductApplicationService;
import org.acme.catalog.domain.ids.ProductId;
import org.acme.dto.PagedResponse;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/products")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Products", description = "CRUD and recommendations for products")
public class ProductResource {

    private final ProductApplicationService productApplicationService;
    private final RecommendationPort recommendations;
    private final CatalogRestMapper mapper;

    @Inject
    public ProductResource(
            ProductApplicationService productApplicationService,
            RecommendationPort recommendations,
            CatalogRestMapper mapper) {
        this.productApplicationService = productApplicationService;
        this.recommendations = recommendations;
        this.mapper = mapper;
    }

    @GET
    @Operation(
            operationId = "listProducts",
            summary = "Product list",
            description = "Paginated list with totalPages, totalElements, currentPage, and data items.")
    @APIResponses({
        @APIResponse(
                responseCode = "200",
                description = "OK",
                content = @Content(schema = @Schema(implementation = PagedResponse.class)))
    })
    public PagedResponse<ProductResponse> getAllProducts(
            @QueryParam("page") @DefaultValue("0") int page, @QueryParam("size") @DefaultValue("20") int size) {
        var result = productApplicationService.list(page, size);
        List<ProductResponse> data = result.items().stream().map(mapper::toApiResponse).toList();
        return new PagedResponse<>(data, result.totalPages(), result.totalElements(), result.pageIndex());
    }

    @GET
    @Path("/{id}")
    @Operation(
            operationId = "getProductById",
            summary = "Product by id",
            description = "Returns one product by id, or 404 if not found.")
    @APIResponses({
        @APIResponse(
                responseCode = "200",
                description = "Found",
                content = @Content(schema = @Schema(implementation = ProductResponse.class))),
        @APIResponse(responseCode = "404", description = "Not found")
    })
    public ProductResponse getProduct(@PathParam("id") Long id) {
        return mapper.toApiResponse(productApplicationService.getById(new ProductId(id)));
    }

    @POST
    @Operation(
            operationId = "createProduct",
            summary = "Create product",
            description = "Creates a new product; returns 201 with Location and body, or validation/category errors.")
    @APIResponses({
        @APIResponse(
                responseCode = "201",
                description = "Created",
                content = @Content(schema = @Schema(implementation = ProductResponse.class))),
        @APIResponse(responseCode = "400", description = "Validation error"),
        @APIResponse(responseCode = "404", description = "Category not found (if categoryId provided)")
    })
    public Response createProduct(@Valid ProductRequest request) {
        var created = productApplicationService.create(mapper.toCommand(request));
        ProductResponse body = mapper.toApiResponse(created);
        return Response.created(URI.create("/products/" + body.id())).entity(body).build();
    }

    @PUT
    @Path("/{id}")
    @Operation(
            operationId = "updateProduct",
            summary = "Update product",
            description = "Replaces fields of an existing product; 404 if product or category missing.")
    @APIResponses({
        @APIResponse(
                responseCode = "200",
                description = "Updated",
                content = @Content(schema = @Schema(implementation = ProductResponse.class))),
        @APIResponse(responseCode = "400", description = "Validation error"),
        @APIResponse(responseCode = "404", description = "Product or category not found")
    })
    public ProductResponse updateProduct(@PathParam("id") Long id, @Valid ProductRequest request) {
        return mapper.toApiResponse(
                productApplicationService.update(new ProductId(id), mapper.toCommand(request)));
    }

    @DELETE
    @Path("/{id}")
    @Operation(
            operationId = "deleteProduct",
            summary = "Delete product",
            description = "Deletes a product by id; 204 on success, 404 if not found.")
    @APIResponses({
        @APIResponse(responseCode = "204", description = "Deleted"),
        @APIResponse(responseCode = "404", description = "Not found")
    })
    public Response deleteProduct(@PathParam("id") Long id) {
        productApplicationService.delete(new ProductId(id));
        return Response.noContent().build();
    }

    @GET
    @Path("/{id}/recommendations")
    @Operation(
            operationId = "getProductRecommendations",
            summary = "Product recommendations",
            description = "Returns recommendation payload for a product; fault tolerance (delay) may apply.")
    @APIResponses({@APIResponse(responseCode = "200", description = "OK"), @APIResponse(responseCode = "404", description = "Not found")})
    public Response getRecommendations(@PathParam("id") Long id) {
        return Response.ok(recommendations.recommendForProduct(new ProductId(id))).build();
    }
}
