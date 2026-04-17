package org.acme.catalog.adapter.in.rest.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "Category", description = "Product category")
public record CategoryResponse(
        @Schema(required = true, example = "1") Long id,
        @Schema(required = true, example = "Electronics") String name) {}
