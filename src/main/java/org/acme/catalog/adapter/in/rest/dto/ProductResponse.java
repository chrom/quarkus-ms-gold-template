package org.acme.catalog.adapter.in.rest.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(
        name = "Product",
        description = "Product in the catalog",
        requiredProperties = {"id", "name", "price"})
public record ProductResponse(
        @Schema(examples = {"1"}) Long id,
        @Schema(examples = {"Coffee"}) String name,
        @Schema(examples = {"12.50"}) double price,
        @Schema(description = "Category id if set", nullable = true) Long categoryId) {}
