package org.acme.catalog.domain.model;

import java.util.Objects;
import java.util.Optional;

import org.acme.catalog.domain.ids.CategoryId;

/**
 * Aggregate root: product taxonomy node. Another aggregate ({@link Product}) references it only by {@link CategoryId}.
 */
public record Category(Optional<CategoryId> id, String name) {

    public Category {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("category name must not be blank");
        }
        if (name.length() > 50) {
            throw new IllegalArgumentException("category name must be at most 50 characters");
        }
    }

    public static Category newCategory(String name) {
        return new Category(Optional.empty(), name);
    }

    public Category withId(CategoryId newId) {
        return new Category(Optional.of(newId), name);
    }
}
