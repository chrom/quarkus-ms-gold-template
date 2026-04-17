package org.acme.catalog.application.port.out;

import java.util.List;

import org.acme.catalog.domain.ids.ProductId;

/**
 * Outbound port for product recommendations (external or simulated provider).
 */
public interface RecommendationPort {

    List<String> recommendForProduct(ProductId productId);
}
