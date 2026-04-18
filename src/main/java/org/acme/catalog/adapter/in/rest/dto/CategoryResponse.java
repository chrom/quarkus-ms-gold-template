package org.acme.catalog.adapter.in.rest.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(
        name = "Category",
        description = "Product category",
        requiredProperties = {"id", "name"})
public record CategoryResponse(
        @Schema(examples = {"1"}) Long id,
        @Schema(examples = {"Electronics"}) String name) {}
