package com.sentinel.db;

import com.lumebridge.ai.EmbeddingUtil;
import com.lumebridge.cache.SemanticCacheContextBuilder;
import com.lumebridge.db.SemanticCacheRepository;
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
import java.sql.Timestamp;
import java.time.Instant;

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
                        scope TEXT NOT NULL,
                        cache_key TEXT NOT NULL,
                        embedding vector(64) NOT NULL,
                        cached_response JSONB NOT NULL,
                        expires_at TIMESTAMPTZ NULL,
                        PRIMARY KEY (scope, cache_key)
                    )
                    """);
            st.execute("""
                    CREATE INDEX idx_semantic_hnsw ON semantic_cache_entries USING hnsw (embedding vector_cosine_ops)
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
    void findClosestReturnsInsertedRowInSameScope() throws Exception {
        SemanticCacheRepository repo = new SemanticCacheRepository(ds, 64);
        String scope = SemanticCacheContextBuilder.cacheScope("tenant-x", "TIME_UNSPECIFIED");
        float[] emb = EmbeddingUtil.unitEmbeddingFromPayload("cache-me".getBytes(), 64);
        repo.upsertEntry(scope, "k1", emb, "{\"cached\":true}");
        float[] query = EmbeddingUtil.unitEmbeddingFromPayload("cache-me".getBytes(), 64);
        var hit = repo.findClosest(query, 0.9, scope);
        assertTrue(hit.isPresent());
        assertEquals(1.0, hit.get().similarity(), 0.02);
    }

    @Test
    void findClosestEmptyWhenSimilarityBelowThreshold() throws Exception {
        SemanticCacheRepository repo = new SemanticCacheRepository(ds, 64);
        String scope = SemanticCacheContextBuilder.cacheScope("tenant-x", "TIME_UNSPECIFIED");
        float[] emb = EmbeddingUtil.unitEmbeddingFromPayload("only-key".getBytes(), 64);
        repo.upsertEntry(scope, "only", emb, "{}");
        float[] query = EmbeddingUtil.unitEmbeddingFromPayload("totally-different-payload".getBytes(), 64);
        assertFalse(repo.findClosest(query, 1.0, scope).isPresent());
    }

    @Test
    void rowsInOtherScopeAreInvisible() throws Exception {
        SemanticCacheRepository repo = new SemanticCacheRepository(ds, 64);
        byte[] rawToday = "{\"prompt\":\"What's the weather in NYC today?\",\"nonce\":1}".getBytes();
        byte[] rawYesterday = "{\"prompt\":\"What was the weather in NYC yesterday?\",\"nonce\":2}".getBytes();
        String tenant = SemanticCacheContextBuilder.tenantFingerprint("sk-sentinel-test");
        String fToday = SemanticCacheContextBuilder.freshnessBucket(rawToday);
        String fYesterday = SemanticCacheContextBuilder.freshnessBucket(rawYesterday);
        String scopeToday = SemanticCacheContextBuilder.cacheScope(tenant, fToday);
        String scopeYesterday = SemanticCacheContextBuilder.cacheScope(tenant, fYesterday);
        byte[] matToday = SemanticCacheContextBuilder.embeddingMaterial(rawToday, tenant, fToday);
        float[] embToday = EmbeddingUtil.unitEmbeddingFromPayload(matToday, 64);
        repo.upsertEntry(scopeToday, "weather", embToday, "{\"day\":\"today\"}");

        byte[] matYesterday = SemanticCacheContextBuilder.embeddingMaterial(rawYesterday, tenant, fYesterday);
        float[] queryYesterday = EmbeddingUtil.unitEmbeddingFromPayload(matYesterday, 64);
        assertFalse(repo.findClosest(queryYesterday, 0.5, scopeYesterday).isPresent());

        assertTrue(repo.findClosest(embToday, 0.5, scopeToday).isPresent());
    }

    @Test
    void expiredEntriesAreIgnored() throws Exception {
        SemanticCacheRepository repo = new SemanticCacheRepository(ds, 64);
        String scope = SemanticCacheContextBuilder.cacheScope("tenant-x", "TIME_UNSPECIFIED");
        float[] emb = EmbeddingUtil.unitEmbeddingFromPayload("expire-me".getBytes(), 64);
        repo.upsertEntry(scope, "old", emb, "{}", Timestamp.from(Instant.now().minusSeconds(600)));
        assertFalse(repo.findClosest(emb, 0.5, scope).isPresent());
    }
}
