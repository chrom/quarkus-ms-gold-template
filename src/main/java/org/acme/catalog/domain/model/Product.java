package org.acme.catalog.domain.model;

import java.util.Objects;
import java.util.Optional;

import org.acme.catalog.domain.ids.CategoryId;
import org.acme.catalog.domain.ids.ProductId;

/**
 * Aggregate root: sellable catalog item. Optional link to {@link Category} is only {@link CategoryId} — not a nested entity graph.
 */
public record Product(
        Optional<ProductId> id,
        String name,
        Money price,
        Optional<CategoryId> categoryId) {

    public Product {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(price, "price");
        Objects.requireNonNull(categoryId, "categoryId");
        if (name.isBlank()) {
            throw new IllegalArgumentException("product name must not be blank");
        }
    }

    public static Product newProduct(String name, Money price, Optional<CategoryId> categoryId) {
        return new Product(Optional.empty(), name, price, categoryId);
    }

    public Product withId(ProductId newId) {
        return new Product(Optional.of(newId), name, price, categoryId);
    }

    public Product rename(String newName) {
        return new Product(id, newName, price, categoryId);
    }

    public Product withPrice(Money newPrice) {
        return new Product(id, name, newPrice, categoryId);
    }

    public Product assignCategory(Optional<CategoryId> newCategoryId) {
        return new Product(id, name, price, newCategoryId);
    }
}
