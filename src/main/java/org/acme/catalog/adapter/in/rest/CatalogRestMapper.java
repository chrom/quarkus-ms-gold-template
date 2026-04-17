package org.acme.catalog.adapter.in.rest;

import java.util.Optional;

import org.acme.catalog.adapter.in.rest.dto.CategoryResponse;
import org.acme.catalog.adapter.in.rest.dto.ProductResponse;
import org.acme.catalog.adapter.in.rest.request.ProductRequest;
import org.acme.catalog.application.service.ProductWriteCommand;
import org.acme.catalog.domain.model.Category;
import org.acme.catalog.domain.model.Product;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CatalogRestMapper {

    /** For responses the id is always present; avoids null id in record when mapping persisted product. */
    public ProductResponse toApiResponse(Product domain) {
        Long id = domain.id().map(i -> i.value()).orElseThrow(() -> new IllegalStateException("persisted product must have id"));
        return new ProductResponse(
                id,
                domain.name(),
                domain.price().toDouble(),
                domain.categoryId().map(c -> c.value()).orElse(null));
    }

    public CategoryResponse toApi(Category domain) {
        Long id = domain.id().map(i -> i.value()).orElseThrow(() -> new IllegalStateException("persisted category must have id"));
        return new CategoryResponse(id, domain.name());
    }

    public ProductWriteCommand toCommand(ProductRequest request) {
        Optional<Long> category =
                request.categoryId() == null ? Optional.empty() : Optional.of(request.categoryId());
        return new ProductWriteCommand(request.name(), request.price(), category);
    }
}
