package org.acme.catalog.domain.exception;

import org.acme.catalog.domain.ids.ProductId;

public final class ProductNotFoundException extends CatalogNotFoundException {

    public ProductNotFoundException(ProductId id) {
        super("Product with id " + id.value() + " not found");
    }
}
