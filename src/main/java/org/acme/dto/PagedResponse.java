package org.acme.dto;

import java.util.List;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Paged response: page data and metadata")
public record PagedResponse<T>(
    @Schema(description = "Items of the current page") List<T> data,
    @Schema(description = "Total number of pages") int totalPages,
    @Schema(description = "Total number of elements") long totalElements,
    @Schema(description = "Current page number (0-based)") int currentPage
) {}
