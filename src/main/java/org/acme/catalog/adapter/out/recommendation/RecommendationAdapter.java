package org.acme.catalog.adapter.out.recommendation;

import java.util.List;

import org.acme.catalog.application.port.out.RecommendationPort;
import org.acme.catalog.domain.ids.ProductId;
import org.acme.service.RecommendationService;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class RecommendationAdapter implements RecommendationPort {

    private final RecommendationService recommendationService;

    @Inject
    public RecommendationAdapter(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @Override
    public List<String> recommendForProduct(ProductId productId) {
        try {
            return recommendationService.getRecommendations(productId.value());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Recommendations call interrupted", e);
        }
    }
}
