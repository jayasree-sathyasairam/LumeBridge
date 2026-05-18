package com.lumebridge.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;

/**
 * pgvector-backed nearest-neighbour lookup for {@link com.lumebridge.plugin.SemanticCachePlugin}.
 * Rows are partitioned by {@code scope} (tenant + freshness bucket) so embeddings cannot bleed across contexts.
 */
public class SemanticCacheRepository {

    private final DataSource dataSource;
    private final int dimensions;

    public SemanticCacheRepository(DataSource dataSource, int dimensions) {
        this.dataSource = dataSource;
        this.dimensions = dimensions;
    }

    public int embeddingDimensions() {
        return dimensions;
    }

    public record CacheHit(String cacheKey, String cachedResponseJson, double similarity) {}

    /**
     * Closest row within {@code scope} by cosine distance ({@code <=>}) if similarity (1 - distance)
     * is at least {@code threshold}.
     */
    public Optional<CacheHit> findClosest(float[] query, double threshold, String scope) throws SQLException {
        if (scope == null || scope.isBlank()) {
            throw new IllegalArgumentException("scope required");
        }
        if (query.length != dimensions) {
            throw new IllegalArgumentException("query dimension " + query.length + " != " + dimensions);
        }
        String vecLiteral = com.lumebridge.ai.EmbeddingUtil.toPgVectorLiteral(query);
        String sql = """
            SELECT cache_key, cached_response::text,
                   (1 - (embedding <=> ?::vector)) AS sim
            FROM semantic_cache_entries
            WHERE scope = ?
              AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
            ORDER BY embedding <=> ?::vector
            LIMIT 1
            """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, vecLiteral);
            ps.setString(2, scope);
            ps.setString(3, vecLiteral);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                String key = rs.getString(1);
                String body = rs.getString(2);
                double sim = rs.getDouble(3);
                if (sim < threshold) {
                    return Optional.empty();
                }
                return Optional.of(new CacheHit(key, body, sim));
            }
        }
    }

    /** Test / admin helper — insert or replace a cache row within {@code scope}. */
    public void upsertEntry(String scope, String cacheKey, float[] embedding, String cachedResponseJson)
            throws SQLException {
        upsertEntry(scope, cacheKey, embedding, cachedResponseJson, null);
    }

    /**
     * Same as {@link #upsertEntry(String, String, float[], String)} with optional absolute expiry.
     * {@code null} means no expiry (row stays eligible until deleted).
     */
    public void upsertEntry(String scope, String cacheKey, float[] embedding, String cachedResponseJson,
                            Timestamp expiresAt) throws SQLException {
        if (scope == null || scope.isBlank()) {
            throw new IllegalArgumentException("scope required");
        }
        if (embedding.length != dimensions) {
            throw new IllegalArgumentException("embedding dimension mismatch");
        }
        String vecLiteral = com.lumebridge.ai.EmbeddingUtil.toPgVectorLiteral(embedding);
        String sql = """
            INSERT INTO semantic_cache_entries (scope, cache_key, embedding, cached_response, expires_at)
            VALUES (?, ?, ?::vector, ?::jsonb, ?)
            ON CONFLICT (scope, cache_key) DO UPDATE SET
              embedding = EXCLUDED.embedding,
              cached_response = EXCLUDED.cached_response,
              expires_at = EXCLUDED.expires_at
            """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, scope);
            ps.setString(2, cacheKey);
            ps.setString(3, vecLiteral);
            ps.setString(4, cachedResponseJson);
            if (expiresAt == null) {
                ps.setNull(5, java.sql.Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                ps.setTimestamp(5, expiresAt);
            }
            ps.executeUpdate();
        }
    }
}
