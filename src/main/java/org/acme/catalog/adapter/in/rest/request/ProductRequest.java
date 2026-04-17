package org.acme.catalog.adapter.in.rest.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "ProductRequest", description = "Fields for creating and updating a product")
public record ProductRequest(
        @Schema(description = "Name", required = true, example = "Laptop")
        @NotBlank(message = "Product name cannot be empty") String name,
        @Schema(description = "Price ≥ 0", required = true, example = "999.99")
        @Min(value = 0, message = "Price cannot be negative") double price,
        @Schema(description = "Optional category link", nullable = true) Long categoryId) {}
