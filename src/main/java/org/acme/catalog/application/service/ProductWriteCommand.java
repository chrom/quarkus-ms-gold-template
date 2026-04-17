package org.acme.catalog.application.service;

import java.util.Optional;

/**
 * Input for create/update product use cases — framework-agnostic (no JAX-RS / Bean Validation types).
 */
public record ProductWriteCommand(String name, double price, Optional<Long> categoryId) {}
