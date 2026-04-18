package org.acme.catalog.adapter.out.recommendation;

import java.util.List;
import java.util.Random;

import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;

import io.micrometer.core.annotation.Counted;
import io.opentelemetry.instrumentation.annotations.WithSpan;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Simulated outbound call to a flaky recommendations service — kept in the catalog bounded
 * context (see ADR 0007) as a concrete infrastructure adapter, accessed only through
 * {@link RecommendationAdapter} and {@code RecommendationPort}.
 *
 * <p>Relocated from {@code org.acme.service} to {@code org.acme.catalog.adapter.out.recommendation}
 * as part of ADR 0012 / Wave 2 Block C. An ArchUnit rule forbids any new code landing under the
 * legacy {@code org.acme.service..} package so this drift cannot reappear.
 */
@ApplicationScoped
public class RecommendationService {

    private final Random random = new Random();

    /**
     * Simulates an unstable network call.
     */
    @Timeout(250) // If waiting more than 250ms - the method ends with TimeoutException
    @Retry(maxRetries = 2) // In case of error or timeout - the framework automatically retries 2 more times
    @Fallback(fallbackMethod = "fallbackRecommendations") // If all 3 attempts fail - execute plan "B"
    @WithSpan
    public List<String> getRecommendations(Long productId) throws InterruptedException {
        int chance = random.nextInt(100);

        if (chance < 30) {
            // 30% chance: Service hangs (3 seconds)
            Thread.sleep(3000);
        } else if (chance < 60) {
            // 30% chance: Service rejected the request (simulating Network Error)
            throw new RuntimeException("Connection Failed");
        }

        // 40% chance: Success
        return List.of("Laptop Sleeve", "Wireless Mouse");
    }

    /**
     * "Plan B" (Fallback). Mandatory condition: parameters and return type must exactly match the original method.
     */
    @Counted(value = "recommendations.fallback.count", description = "How many times Fallback was triggered for recommendations")
    @WithSpan
    public List<String> fallbackRecommendations(Long productId) {
        return List.of("Gift Certificate (always available)");
    }
}
