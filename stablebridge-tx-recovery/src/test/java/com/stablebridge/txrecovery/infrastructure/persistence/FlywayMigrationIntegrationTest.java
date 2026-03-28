package com.stablebridge.txrecovery.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;

import com.stablebridge.txrecovery.testutil.PgTest;

@PgTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class FlywayMigrationIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private DataSource dataSource;

    @Test
    void shouldRunAllRequiredMigrations() {
        // given
        var requiredVersions = List.of("1", "2", "3", "4", "5", "6", "7", "8", "9");

        // when
        var appliedVersions = Arrays.stream(flyway.info().applied())
                .map(MigrationInfo::getVersion)
                .map(Object::toString)
                .toList();

        // then
        assertThat(appliedVersions).containsAll(requiredVersions);
    }

    @Test
    void shouldCreateAllTables() throws Exception {
        // given
        var expectedTables = List.of(
                "address_pool",
                "transaction_projection",
                "nonce_account_pool",
                "escalation_policy",
                "gas_budget_config");

        // when
        List<String> actualTables;
        try (var connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            ResultSet rs = metaData.getTables(null, "public", null, new String[] {"TABLE"});
            actualTables = new ArrayList<>();
            while (rs.next()) {
                actualTables.add(rs.getString("TABLE_NAME"));
            }
        }

        // then
        assertThat(actualTables).containsAll(expectedTables);
    }

    @Test
    void shouldSeedDefaultEscalationPolicies() throws Exception {
        // when
        int count;
        try (var connection = dataSource.getConnection();
                var stmt = connection.createStatement();
                var rs = stmt.executeQuery("SELECT COUNT(*) FROM escalation_policy WHERE chain = '*'")) {
            rs.next();
            count = rs.getInt(1);
        }

        // then
        assertThat(count).isEqualTo(8);
    }

    @Test
    void shouldSeedDefaultGasBudgetConfig() throws Exception {
        // when / then
        try (var connection = dataSource.getConnection();
                var stmt = connection.createStatement();
                var rs = stmt.executeQuery(
                        "SELECT percentage, absolute_min, absolute_max FROM gas_budget_config WHERE chain = '*'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getBigDecimal("percentage")).isEqualByComparingTo("0.01");
            assertThat(rs.getBigDecimal("absolute_min")).isEqualByComparingTo("5");
            assertThat(rs.getBigDecimal("absolute_max")).isEqualByComparingTo("500");
        }
    }
}
