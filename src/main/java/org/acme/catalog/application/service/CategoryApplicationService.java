package org.acme.catalog.application.service;

import java.util.List;

import org.acme.catalog.application.port.out.CategoryRepositoryPort;
import org.acme.catalog.domain.exception.CategoryNotFoundException;
import org.acme.catalog.domain.ids.CategoryId;
import org.acme.catalog.domain.model.Category;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Read-side use cases for categories — REST calls this instead of {@link CategoryRepositoryPort} directly.
 */
@ApplicationScoped
public class CategoryApplicationService {

    private final CategoryRepositoryPort categories;

    @Inject
    public CategoryApplicationService(CategoryRepositoryPort categories) {
        this.categories = categories;
    }

    public List<Category> listAllOrderByName() {
        return categories.listAllOrderByName();
    }

    public Category getById(CategoryId id) {
        return categories.findById(id).orElseThrow(() -> new CategoryNotFoundException(id));
    }
}
