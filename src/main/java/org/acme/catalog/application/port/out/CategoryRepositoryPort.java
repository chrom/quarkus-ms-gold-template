package org.acme.catalog.application.port.out;

import java.util.List;
import java.util.Optional;

import org.acme.catalog.domain.model.Category;
import org.acme.catalog.domain.ids.CategoryId;

public interface CategoryRepositoryPort {

    Optional<Category> findById(CategoryId id);

    boolean existsById(CategoryId id);

    long count();

    Category save(Category category);

    List<Category> listAllOrderByName();
}
