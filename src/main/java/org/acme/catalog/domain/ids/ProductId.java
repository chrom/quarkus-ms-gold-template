package org.acme.catalog.domain.ids;

import java.util.Objects;

/**
 * Identity of the {@link org.acme.catalog.domain.model.Product} aggregate.
 */
public record ProductId(Long value) {
    public ProductId {
        Objects.requireNonNull(value, "product id");
    }
}
