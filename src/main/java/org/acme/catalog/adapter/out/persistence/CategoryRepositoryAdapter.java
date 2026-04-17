package org.acme.catalog.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import org.acme.catalog.application.port.out.CategoryRepositoryPort;
import org.acme.catalog.adapter.out.persistence.jpa.CategoryEntity;
import org.acme.catalog.domain.model.Category;
import org.acme.catalog.domain.ids.CategoryId;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class CategoryRepositoryAdapter implements CategoryRepositoryPort {

    private final CategoryEntityRepository repository;
    private final CategoryPersistenceMapper mapper;

    @Inject
    public CategoryRepositoryAdapter(CategoryEntityRepository repository, CategoryPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Category> findById(CategoryId id) {
        return repository.findByIdOptional(id.value()).map(mapper::toDomain);
    }

    @Override
    public boolean existsById(CategoryId id) {
        return repository.findByIdOptional(id.value()).isPresent();
    }

    @Override
    public long count() {
        return repository.count();
    }

    @Override
    @Transactional
    public Category save(Category category) {
        if (category.id().isEmpty()) {
            CategoryEntity entity = new CategoryEntity();
            mapper.merge(category, entity);
            repository.persist(entity);
            repository.flush();
            return mapper.toDomain(entity);
        }
        CategoryEntity entity =
                repository
                        .findByIdOptional(category.id().get().value())
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "category id present but row missing: " + category.id().get().value()));
        mapper.merge(category, entity);
        return mapper.toDomain(entity);
    }

    @Override
    public List<Category> listAllOrderByName() {
        return repository.list("order by name").stream().map(mapper::toDomain).toList();
    }
}
