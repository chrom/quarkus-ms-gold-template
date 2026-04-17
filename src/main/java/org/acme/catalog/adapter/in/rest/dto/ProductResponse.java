package org.acme.catalog.adapter.in.rest.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "Product", description = "Product in the catalog")
public record ProductResponse(
        @Schema(required = true, example = "1") Long id,
        @Schema(required = true, example = "Coffee") String name,
        @Schema(required = true, example = "12.50") double price,
        @Schema(description = "Category id if set", nullable = true) Long categoryId) {}
