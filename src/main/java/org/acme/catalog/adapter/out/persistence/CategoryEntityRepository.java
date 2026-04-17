package org.acme.catalog.adapter.out.persistence;

import org.acme.catalog.adapter.out.persistence.jpa.CategoryEntity;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CategoryEntityRepository implements PanacheRepository<CategoryEntity> {}
