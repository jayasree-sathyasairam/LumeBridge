package com.lumebridge.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * pgvector-backed nearest-neighbour lookup for {@link com.lumebridge.plugin.SemanticCachePlugin}.
 */
public class SemanticCacheRepository {

    private final DataSource dataSource;
    private final int dimensions;

    public SemanticCacheRepository(DataSource dataSource, int dimensions) {
        this.dataSource = dataSource;
        this.dimensions = dimensions;
    }

    public record CacheHit(String cacheKey, String cachedResponseJson, double similarity) {}

    /**
     * Returns the closest row by cosine distance ({@code <=>}) if similarity (1 - distance) is at least {@code threshold}.
     */
    public Optional<CacheHit> findClosest(float[] query, double threshold) throws SQLException {
        if (query.length != dimensions) {
            throw new IllegalArgumentException("query dimension " + query.length + " != " + dimensions);
        }
        String vecLiteral = com.lumebridge.ai.EmbeddingUtil.toPgVectorLiteral(query);
        String sql = """
            SELECT cache_key, cached_response::text,
                   (1 - (embedding <=> ?::vector)) AS sim
            FROM semantic_cache_entries
            ORDER BY embedding <=> ?::vector
            LIMIT 1
            """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, vecLiteral);
            ps.setString(2, vecLiteral);
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

    /** Test / admin helper — insert or replace a cache row. */
    public void upsertEntry(String cacheKey, float[] embedding, String cachedResponseJson) throws SQLException {
        if (embedding.length != dimensions) {
            throw new IllegalArgumentException("embedding dimension mismatch");
        }
        String vecLiteral = com.lumebridge.ai.EmbeddingUtil.toPgVectorLiteral(embedding);
        String sql = """
            INSERT INTO semantic_cache_entries (cache_key, embedding, cached_response)
            VALUES (?, ?::vector, ?::jsonb)
            ON CONFLICT (cache_key) DO UPDATE SET
              embedding = EXCLUDED.embedding,
              cached_response = EXCLUDED.cached_response
            """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, cacheKey);
            ps.setString(2, vecLiteral);
            ps.setString(3, cachedResponseJson);
            ps.executeUpdate();
        }
    }
}
