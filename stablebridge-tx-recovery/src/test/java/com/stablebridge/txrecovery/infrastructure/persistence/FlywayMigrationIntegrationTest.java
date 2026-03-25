package com.stablebridge.txrecovery.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;

import com.stablebridge.txrecovery.support.PgTest;

@PgTest
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
class FlywayMigrationIntegrationTest {

    @Autowired
    private Flyway flyway;

    @Autowired
    private DataSource dataSource;

    @Test
    void shouldRunAllMigrations() {
        // given
        var migrationInfo = flyway.info().applied();

        // when
        var migrationCount = migrationInfo.length;

        // then
        assertThat(migrationCount).isEqualTo(5);
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
}
