package com.lumebridge.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@Tag("integration")
class TaskRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("sn")
            .withUsername("sn")
            .withPassword("sn");

    static HikariDataSource ds;

    @BeforeAll
    static void setup() throws Exception {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(POSTGRES.getJdbcUrl());
        cfg.setUsername(POSTGRES.getUsername());
        cfg.setPassword(POSTGRES.getPassword());
        cfg.setMaximumPoolSize(2);
        ds = new HikariDataSource(cfg);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("DROP TABLE IF EXISTS tasks");
            st.execute("""
                    CREATE TABLE tasks (
                        payload_hash    TEXT PRIMARY KEY,
                        payload         JSONB,
                        result          JSONB,
                        status          TEXT NOT NULL DEFAULT 'pending',
                        version         INT NOT NULL DEFAULT 1,
                        response_hash   TEXT
                    )
                    """);
        }
    }

    @BeforeEach
    void truncate() throws Exception {
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("TRUNCATE tasks");
        }
    }

    @AfterAll
    static void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void upsertCompletedIncrementsVersion() throws Exception {
        TaskRepository repo = new TaskRepository(ds);
        repo.upsertCompleted("h1", "{\"a\":1}", "{\"ok\":true}", "rh1");
        var row1 = repo.findByPayloadHash("h1");
        assertTrue(row1.isPresent());
        assertEquals(1, row1.get().version());
        assertEquals("rh1", row1.get().responseHash());

        repo.upsertCompleted("h1", "{\"a\":2}", "{\"ok\":false}", "rh2");
        var row2 = repo.findByPayloadHash("h1");
        assertTrue(row2.isPresent());
        assertEquals(2, row2.get().version());
        assertEquals("rh2", row2.get().responseHash());
    }

    @Test
    void overwriteResultUpdatesRow() throws Exception {
        TaskRepository repo = new TaskRepository(ds);
        repo.upsertCompleted("h2", "{}", "{\"v\":1}", "r1");
        repo.overwriteResult("h2", "{\"v\":2}", "r2");
        var row = repo.findByPayloadHash("h2");
        assertTrue(row.isPresent());
        assertEquals("r2", row.get().responseHash());
        assertEquals(2, row.get().version());
    }
}
