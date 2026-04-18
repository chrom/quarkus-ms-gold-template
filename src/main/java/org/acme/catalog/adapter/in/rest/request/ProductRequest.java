package org.acme.catalog.adapter.in.rest.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(
        name = "ProductRequest",
        description = "Fields for creating and updating a product",
        requiredProperties = {"name", "price"})
public record ProductRequest(
        @Schema(description = "Name", examples = {"Laptop"})
        @NotBlank(message = "Product name cannot be empty") String name,
        @Schema(description = "Price ≥ 0", examples = {"999.99"})
        @Min(value = 0, message = "Price cannot be negative") double price,
        @Schema(description = "Optional category link", nullable = true) Long categoryId) {}
