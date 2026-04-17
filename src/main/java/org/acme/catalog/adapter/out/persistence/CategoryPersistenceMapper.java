package org.acme.catalog.adapter.out.persistence;

import java.util.Optional;

import org.acme.catalog.adapter.out.persistence.jpa.CategoryEntity;
import org.acme.catalog.domain.ids.CategoryId;
import org.acme.catalog.domain.model.Category;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CategoryPersistenceMapper {

    public Category toDomain(CategoryEntity entity) {
        return new Category(Optional.of(new CategoryId(entity.getId())), entity.getName());
    }

    public void merge(Category domain, CategoryEntity target) {
        target.setName(domain.name());
    }
}
