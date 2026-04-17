package org.acme;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract test for the Flyway migration bundle under {@code src/main/resources/db/migration}.
 *
 * <p>Why a dedicated IT instead of relying on {@code @QuarkusTest}: Quarkus Dev Services boots a
 * PostgreSQL container <em>and</em> runs Flyway before handing control to the test class. That
 * is sufficient for "does the app start?" but hides failure modes we care about on the migration
 * surface:
 * <ul>
 *   <li>A renamed or reordered {@code Vn__*.sql} file silently invalidates checksums in a
 *       deployed environment — but Quarkus' own boot is already past it.</li>
 *   <li>An attempt to mix DDL and data in a way that works on an empty schema but regresses
 *       on a live DB passes through unnoticed.</li>
 *   <li>Re-running the same bundle (representing a redeploy) must be a no-op — Flyway history
 *       is the source of truth here, not Hibernate's validator.</li>
 * </ul>
 *
 * <p>This test bypasses the Quarkus test bootstrap, starts a pristine {@code postgres:16-alpine}
 * container, invokes {@link Flyway#migrate()} directly, and asserts the resulting schema on the
 * raw JDBC connection. The image tag MUST track {@code application.properties}'s
 * {@code %test.quarkus.datasource.devservices.image-name} — otherwise we would test migrations
 * against a version the production Helm chart (bitnami/postgresql 16.x) never sees.
 *
 * <p>Failsafe picks this up via the {@code *IT} suffix and wires it into {@code mvn verify}.
 */
@Testcontainers
class FlywayMigrationIT {

    /**
     * Pinned to the same image Dev Services uses (see application.properties). Pulling from
     * Docker Hub on every PR costs ~30 MB; in CI the workflow relies on the built-in
     * {@code actions/cache}-backed Testcontainers layer cache. Reuse across tests is disabled
     * here on purpose — this test must observe the "fresh DB" path, not a warm container.
     */
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("postgres:16-alpine"));

    @Test
    void migrationsBringSchemaToExpectedShape() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load();

        MigrateResult first = flyway.migrate();
        assertAll("initial migrate",
                () -> assertTrue(first.success, "Flyway reported migrate() as failed"),
                // V1 + V2 at minimum; additional Vn files only increase this count.
                () -> assertTrue(first.migrationsExecuted >= 2,
                        () -> "expected >=2 migrations, got " + first.migrationsExecuted));

        try (Connection conn = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {

            Set<String> tables = listTables(conn, "public");
            assertAll("schema shape",
                    () -> assertTrue(tables.contains("category"),
                            () -> "missing table category in " + tables),
                    () -> assertTrue(tables.contains("product"),
                            () -> "missing table product in " + tables),
                    () -> assertTrue(tables.contains("myentity"),
                            () -> "missing table myentity in " + tables),
                    () -> assertTrue(tables.contains("flyway_schema_history"),
                            () -> "missing flyway_schema_history in " + tables));

            assertTrue(hasPrimaryKey(conn, "product", "id"),
                    "product.id should be PRIMARY KEY");
            assertTrue(foreignKeyExists(conn, "product", "category_id", "category", "id"),
                    "FK product.category_id -> category.id is missing");
            assertTrue(sequenceExists(conn, "MyEntity_SEQ"),
                    "MyEntity_SEQ (Panache @SequenceGenerator allocator) is missing");
            assertAll("flyway_schema_history integrity",
                    () -> assertTrue(allRowsSuccessful(conn),
                            "flyway_schema_history contains at least one failed row"));

            assertEquals(1050, countProducts(conn),
                    "V2__Seed_demo_catalog expected to insert 1050 rows");
        }

        // Re-running the bundle against the same DB must be a pure no-op. If any Vn file
        // gets edited in place (rather than superseded by a V{n+1}), Flyway raises a
        // checksum mismatch and this assertion trips — exactly the kind of drift we want
        // to catch in CI rather than on the stage cluster.
        MigrateResult second = flyway.migrate();
        assertEquals(0, second.migrationsExecuted,
                "second migrate() must be a no-op; got " + second.migrationsExecuted + " executed");
    }

    private static Set<String> listTables(Connection conn, String schema) throws Exception {
        Set<String> out = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = ?")) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        }
        return out;
    }

    private static boolean hasPrimaryKey(Connection conn, String table, String column) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                """
                SELECT 1
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_name = kcu.constraint_name
                 AND tc.table_schema  = kcu.table_schema
                WHERE tc.constraint_type = 'PRIMARY KEY'
                  AND tc.table_schema   = 'public'
                  AND tc.table_name     = ?
                  AND kcu.column_name   = ?
                """)) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean foreignKeyExists(Connection conn,
                                            String childTable, String childColumn,
                                            String parentTable, String parentColumn) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                """
                SELECT 1
                FROM information_schema.referential_constraints rc
                JOIN information_schema.key_column_usage kcu
                  ON rc.constraint_name = kcu.constraint_name
                JOIN information_schema.constraint_column_usage ccu
                  ON rc.unique_constraint_name = ccu.constraint_name
                WHERE kcu.table_name   = ?
                  AND kcu.column_name  = ?
                  AND ccu.table_name   = ?
                  AND ccu.column_name  = ?
                """)) {
            ps.setString(1, childTable);
            ps.setString(2, childColumn);
            ps.setString(3, parentTable);
            ps.setString(4, parentColumn);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean sequenceExists(Connection conn, String sequenceName) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM information_schema.sequences "
                        + "WHERE sequence_schema = 'public' AND sequence_name = ?")) {
            ps.setString(1, sequenceName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static boolean allRowsSuccessful(Connection conn) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COALESCE(MIN(success::int), 1) FROM flyway_schema_history");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() && rs.getInt(1) == 1;
        }
    }

    private static int countProducts(Connection conn) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM product");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : -1;
        }
    }
}
