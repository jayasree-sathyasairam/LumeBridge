package com.lumebridge.db;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class TaskRepository {

    private final DataSource dataSource;

    public TaskRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Optional<TaskRecord> findByPayloadHash(String payloadHash) throws SQLException {
        String sql = """
            SELECT version, response_hash, result::text
            FROM tasks
            WHERE payload_hash = ?
            """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, payloadHash);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                int version = rs.getInt(1);
                String rh = rs.getString(2);
                String result = rs.getString(3);
                return Optional.of(new TaskRecord(version, rh, result));
            }
        }
    }

    /**
     * Insert or update a completed task row (used after successful pipeline).
     */
    public void upsertCompleted(String payloadHash, String payloadJson, String resultJson, String responseHash)
            throws SQLException {
        String sql = """
            INSERT INTO tasks (payload_hash, payload, result, status, version, response_hash)
            VALUES (?, ?, ?::jsonb, 'completed', 1, ?)
            ON CONFLICT (payload_hash) DO UPDATE SET
                payload = EXCLUDED.payload,
                result = EXCLUDED.result,
                response_hash = EXCLUDED.response_hash,
                version = tasks.version + 1,
                status = 'completed',
                updated_at = now()
            """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, payloadHash);
            ps.setString(2, payloadJson == null || payloadJson.isBlank() ? "{}" : payloadJson);
            ps.setString(3, resultJson == null || resultJson.isBlank() ? "null" : resultJson);
            ps.setString(4, responseHash);
            ps.executeUpdate();
        }
    }

    /**
     * Overwrite result for auto_reprocess collision mode (does not bump from conflict logic twice in one request).
     */
    public void overwriteResult(String payloadHash, String resultJson, String responseHash) throws SQLException {
        String sql = """
            UPDATE tasks
            SET result = ?::jsonb,
                response_hash = ?,
                version = version + 1,
                status = 'completed',
                updated_at = now()
            WHERE payload_hash = ?
            """;
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, resultJson == null || resultJson.isBlank() ? "null" : resultJson);
            ps.setString(2, responseHash);
            ps.setString(3, payloadHash);
            int n = ps.executeUpdate();
            if (n == 0) {
                upsertCompleted(payloadHash, "{}", resultJson, responseHash);
            }
        }
    }

    /**
     * Delete tasks older than maxAgeHours in batches.
     */
    public int deleteStale(int maxAgeHours, int limit) throws SQLException {
        String sql = "DELETE FROM tasks WHERE updated_at < now() - (? * interval '1 hour') AND status IN ('completed', 'failed') AND payload_hash IN (SELECT payload_hash FROM tasks WHERE updated_at < now() - (? * interval '1 hour') LIMIT ?)";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, maxAgeHours);
            ps.setInt(2, maxAgeHours);
            ps.setInt(3, limit);
            return ps.executeUpdate();
        }
    }
}
