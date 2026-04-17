package org.acme.health;

import java.sql.Connection;

import javax.sql.DataSource;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Readiness: check JDBC pool to PostgreSQL with validation timeout (seconds).
 */
@ApplicationScoped
@Readiness
public class DatabaseReadinessHealthCheck implements HealthCheck {

    private static final int VALIDATION_TIMEOUT_SECONDS = 5;

    private final DataSource dataSource;

    @Inject
    public DatabaseReadinessHealthCheck(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public HealthCheckResponse call() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(VALIDATION_TIMEOUT_SECONDS)) {
                return HealthCheckResponse.named("postgresql").up().build();
            }
            return HealthCheckResponse.named("postgresql").withData("reason", "isValid returned false").down().build();
        } catch (Exception e) {
            return HealthCheckResponse.named("postgresql").withData("error", e.getMessage()).down().build();
        }
    }
}
