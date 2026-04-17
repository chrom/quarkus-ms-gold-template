package org.acme.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.acme.catalog.application.port.out.CategoryRepositoryPort;
import org.acme.catalog.application.port.out.ProductRepositoryPort;
import org.acme.catalog.domain.model.Category;
import org.acme.catalog.domain.model.Money;
import org.acme.catalog.domain.model.Product;
import org.acme.catalog.domain.ids.CategoryId;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;
import net.datafaker.Faker;

@IfBuildProfile("dev")
@ApplicationScoped
public class TestDataGenerator {

    private final ProductRepositoryPort productRepository;
    private final CategoryRepositoryPort categoryRepository;

    public TestDataGenerator(ProductRepositoryPort productRepository, CategoryRepositoryPort categoryRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public void onStart(@Observes StartupEvent event) {
        if (categoryRepository.count() > 0) {
            return;
        }

        System.out.println("🚀 Starting fake data generation via Datafaker...");

        Faker faker = new Faker();
        List<CategoryId> categoryIds = new ArrayList<>();

        for (int i = 0; i < 5; i++) {
            Category created = categoryRepository.save(Category.newCategory(faker.commerce().department()));
            categoryIds.add(created.id().orElseThrow());
        }

        for (int i = 0; i < 50; i++) {
            CategoryId catId = categoryIds.get(faker.random().nextInt(categoryIds.size()));
            Product product =
                    Product.newProduct(
                            faker.commerce().productName(),
                            Money.of(faker.number().randomDouble(2, 5, 2000)),
                            Optional.of(catId));
            productRepository.save(product);
        }

        System.out.println("✅ Successfully generated 5 categories and 50 products!");
    }
}
