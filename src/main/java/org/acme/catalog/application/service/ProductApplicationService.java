package org.acme.catalog.application.service;

import java.util.Optional;

import org.acme.catalog.application.port.out.CategoryRepositoryPort;
import org.acme.catalog.application.port.out.ProductRepositoryPort;
import org.acme.catalog.domain.exception.CategoryNotFoundException;
import org.acme.catalog.domain.exception.ProductNotFoundException;
import org.acme.catalog.domain.ids.CategoryId;
import org.acme.catalog.domain.ids.ProductId;
import org.acme.catalog.domain.model.Money;
import org.acme.catalog.domain.model.Product;

import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.instrumentation.annotations.SpanAttribute;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;

@ApplicationScoped
public class ProductApplicationService {

    private final ProductRepositoryPort products;
    private final CategoryRepositoryPort categories;
    private final MeterRegistry meterRegistry;

    @Inject
    public ProductApplicationService(
            ProductRepositoryPort products,
            CategoryRepositoryPort categories,
            MeterRegistry meterRegistry) {
        this.products = products;
        this.categories = categories;
        this.meterRegistry = meterRegistry;
    }

    @WithSpan
    @Timed(value = "products.search.time", description = "Product search time in database")
    @Timeout(1000)
    @Retry(maxRetries = 2)
    public ProductRepositoryPort.ProductPage list(
            @SpanAttribute("pagination.page") int pageIndex,
            @SpanAttribute("pagination.size") int pageSize) {
        ProductRepositoryPort.ProductPage page = products.findPage(pageIndex, pageSize);
        Span span = Span.current();
        if (span.isRecording()) {
            span.setAttribute("pagination.total_pages", page.totalPages());
            span.setAttribute("pagination.total_count", page.totalElements());
        }
        return page;
    }

    @WithSpan
    @Timeout(500)
    public Product getById(ProductId id) {
        return products.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    @WithSpan
    @Transactional
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5f, delay = 10_000)
    public Product create(ProductWriteCommand command) {
        try {
            Optional<CategoryId> categoryId = resolveCategory(command.categoryId());
            Product created =
                    Product.newProduct(command.name(), Money.of(command.price()), categoryId);
            Product saved = products.save(created);
            recordProductMutation("create", true);
            return saved;
        } catch (CategoryNotFoundException e) {
            recordProductMutation("create", false);
            throw e;
        }
    }

    @WithSpan
    @Transactional
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5f, delay = 10_000)
    public Product update(ProductId id, ProductWriteCommand command) {
        try {
            Product existing = getById(id);
            Optional<CategoryId> categoryId = resolveCategory(command.categoryId());
            Product updated =
                    existing
                            .rename(command.name())
                            .withPrice(Money.of(command.price()))
                            .assignCategory(categoryId);
            Product saved = products.save(updated);
            recordProductMutation("update", true);
            return saved;
        } catch (ProductNotFoundException | CategoryNotFoundException e) {
            recordProductMutation("update", false);
            throw e;
        }
    }

    @WithSpan
    @Transactional
    public void delete(ProductId id) {
        boolean removed = products.delete(id);
        if (!removed) {
            recordProductMutation("delete", false);
            throw new ProductNotFoundException(id);
        }
        recordProductMutation("delete", true);
    }

    private Optional<CategoryId> resolveCategory(Optional<Long> raw) {
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        CategoryId categoryId = new CategoryId(raw.get());
        if (!categories.existsById(categoryId)) {
            throw new CategoryNotFoundException(categoryId);
        }
        return Optional.of(categoryId);
    }

    private void recordProductMutation(String operation, boolean success) {
        Counter.builder("products.mutations")
                .tag("operation", operation)
                .tag("outcome", success ? "success" : "failure")
                .register(meterRegistry)
                .increment();
    }
}
