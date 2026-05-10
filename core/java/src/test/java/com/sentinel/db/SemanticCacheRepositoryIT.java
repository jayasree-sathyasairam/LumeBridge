package com.lumebridge.db;

import com.lumebridge.ai.EmbeddingUtil;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@Tag("integration")
class SemanticCacheRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
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
        cfg.setMaximumPoolSize(4);
        ds = new HikariDataSource(cfg);
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("CREATE EXTENSION IF NOT EXISTS vector");
            st.execute("DROP TABLE IF EXISTS semantic_cache_entries");
            st.execute("""
                    CREATE TABLE semantic_cache_entries (
                        cache_key TEXT PRIMARY KEY,
                        embedding vector(64) NOT NULL,
                        cached_response JSONB NOT NULL
                    )
                    """);
        }
    }

    @BeforeEach
    void truncate() throws Exception {
        try (var c = ds.getConnection(); Statement st = c.createStatement()) {
            st.execute("TRUNCATE semantic_cache_entries");
        }
    }

    @AfterAll
    static void tearDown() {
        if (ds != null) {
            ds.close();
        }
    }

    @Test
    void findClosestReturnsInsertedRow() throws Exception {
        SemanticCacheRepository repo = new SemanticCacheRepository(ds, 64);
        float[] emb = EmbeddingUtil.unitEmbeddingFromPayload("cache-me".getBytes(), 64);
        repo.upsertEntry("k1", emb, "{\"cached\":true}");
        float[] query = EmbeddingUtil.unitEmbeddingFromPayload("cache-me".getBytes(), 64);
        var hit = repo.findClosest(query, 0.9);
        assertTrue(hit.isPresent());
        assertEquals(1.0, hit.get().similarity(), 0.02);
    }

    @Test
    void findClosestEmptyWhenSimilarityBelowThreshold() throws Exception {
        SemanticCacheRepository repo = new SemanticCacheRepository(ds, 64);
        float[] emb = EmbeddingUtil.unitEmbeddingFromPayload("only-key".getBytes(), 64);
        repo.upsertEntry("only", emb, "{}");
        float[] query = EmbeddingUtil.unitEmbeddingFromPayload("totally-different-payload".getBytes(), 64);
        assertFalse(repo.findClosest(query, 1.0).isPresent());
    }
}
