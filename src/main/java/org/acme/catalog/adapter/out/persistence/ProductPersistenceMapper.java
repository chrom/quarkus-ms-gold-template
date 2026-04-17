package org.acme.catalog.adapter.out.persistence;

import java.util.Optional;

import org.acme.catalog.adapter.out.persistence.jpa.ProductEntity;
import org.acme.catalog.domain.ids.CategoryId;
import org.acme.catalog.domain.ids.ProductId;
import org.acme.catalog.domain.model.Money;
import org.acme.catalog.domain.model.Product;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProductPersistenceMapper {

    public Product toDomain(ProductEntity entity) {
        Optional<ProductId> id = Optional.of(new ProductId(entity.getId()));
        Optional<CategoryId> category =
                entity.getCategoryId() == null
                        ? Optional.empty()
                        : Optional.of(new CategoryId(entity.getCategoryId()));
        return new Product(id, entity.getName(), Money.of(entity.getPrice()), category);
    }

    public void merge(Product domain, ProductEntity target) {
        target.setName(domain.name());
        target.setPrice(domain.price().toDouble());
        target.setCategoryId(domain.categoryId().map(CategoryId::value).orElse(null));
    }
}
