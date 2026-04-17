package org.acme.catalog.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import org.acme.catalog.application.port.out.ProductRepositoryPort;
import org.acme.catalog.adapter.out.persistence.jpa.ProductEntity;
import org.acme.catalog.domain.model.Product;
import org.acme.catalog.domain.ids.ProductId;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ProductRepositoryAdapter implements ProductRepositoryPort {

    private final ProductEntityRepository repository;
    private final ProductPersistenceMapper mapper;

    @Inject
    public ProductRepositoryAdapter(ProductEntityRepository repository, ProductPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Product> findById(ProductId id) {
        return repository.findByIdOptional(id.value()).map(mapper::toDomain);
    }

    @Override
    public ProductPage findPage(int pageIndex, int pageSize) {
        PanacheQuery<ProductEntity> query = repository.findAll().page(pageIndex, pageSize);
        List<Product> items = query.list().stream().map(mapper::toDomain).toList();
        int totalPages = query.pageCount();
        long totalElements = query.count();
        return new ProductPage(items, totalPages, totalElements, pageIndex);
    }

    @Override
    @Transactional
    public Product save(Product product) {
        if (product.id().isEmpty()) {
            ProductEntity entity = new ProductEntity();
            mapper.merge(product, entity);
            repository.persist(entity);
            repository.flush();
            return mapper.toDomain(entity);
        }
        ProductEntity entity =
                repository
                        .findByIdOptional(product.id().get().value())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "product id present but row missing: " + product.id().get().value()));
        mapper.merge(product, entity);
        return mapper.toDomain(entity);
    }

    @Override
    @Transactional
    public boolean delete(ProductId id) {
        return repository.deleteById(id.value());
    }
}
