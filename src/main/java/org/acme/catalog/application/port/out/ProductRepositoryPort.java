package org.acme.catalog.application.port.out;

import java.util.List;
import java.util.Optional;

import org.acme.catalog.domain.model.Product;
import org.acme.catalog.domain.ids.ProductId;

public interface ProductRepositoryPort {

    Optional<Product> findById(ProductId id);

    ProductPage findPage(int pageIndex, int pageSize);

    Product save(Product product);

    boolean delete(ProductId id);

    record ProductPage(List<Product> items, int totalPages, long totalElements, int pageIndex) {}
}
