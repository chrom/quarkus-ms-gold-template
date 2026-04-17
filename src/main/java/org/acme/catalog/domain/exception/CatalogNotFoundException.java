package org.acme.catalog.domain.exception;

/**
 * Base type for missing catalog aggregates — mapped to HTTP 404 in the REST adapter, not thrown as JAX-RS types from the domain.
 */
public abstract class CatalogNotFoundException extends RuntimeException {

    protected CatalogNotFoundException(String message) {
        super(message);
    }
}
