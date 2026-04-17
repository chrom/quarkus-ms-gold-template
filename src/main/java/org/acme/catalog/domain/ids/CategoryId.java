package org.acme.catalog.domain.ids;

import java.util.Objects;

/**
 * Identity of the {@link org.acme.catalog.domain.model.Category} aggregate.
 */
public record CategoryId(Long value) {
    public CategoryId {
        Objects.requireNonNull(value, "category id");
    }
}
