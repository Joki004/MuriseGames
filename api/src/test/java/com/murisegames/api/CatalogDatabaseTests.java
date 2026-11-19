package com.murisegames.api;

import static org.junit.jupiter.api.Assertions.*;
import com.murisegames.api.catalog.GameRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "RUN_DB_TESTS", matches = "true")
class CatalogDatabaseTests {
    @Autowired GameRepository games;
    @Autowired JdbcTemplate jdbc;

    @Test
    void migrationsCreateCatalogAndRuntimeCannotModifyItOrMigrationHistory() {
        assertEquals(9, games.count());
        assertTrue(games.findAll().stream().allMatch(game -> game.getRulesStatus().equals("PENDING_REVIEW")));
        assertEquals(Boolean.FALSE, jdbc.queryForObject(
            "select has_table_privilege(current_user, 'murise.games', 'INSERT')", Boolean.class));
        assertEquals(Boolean.FALSE, jdbc.queryForObject(
            "select has_table_privilege(current_user, 'murise.flyway_schema_history', 'UPDATE')", Boolean.class));
    }
}
