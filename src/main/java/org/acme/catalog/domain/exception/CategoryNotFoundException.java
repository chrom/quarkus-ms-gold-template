package org.acme.catalog.domain.exception;

import org.acme.catalog.domain.ids.CategoryId;

public final class CategoryNotFoundException extends CatalogNotFoundException {

    public CategoryNotFoundException(CategoryId id) {
        super("Category with id " + id.value() + " not found");
    }
}
