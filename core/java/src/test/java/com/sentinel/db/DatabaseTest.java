package com.lumebridge.db;

import com.lumebridge.SentinelConstants;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseTest {

    @Test
    void resolveJdbcUrlUsesDefaults() {
        String url = Database.resolveJdbcUrl(Map.of());
        assertTrue(url.startsWith(SentinelConstants.JDBC_PREFIX));
        assertTrue(url.contains(SentinelConstants.DEFAULT_POSTGRES_HOST));
        assertTrue(url.endsWith("/" + SentinelConstants.DEFAULT_POSTGRES_DB));
    }

    @Test
    void resolveJdbcUrlHonorsHostPortDbOverrides() {
        String url = Database.resolveJdbcUrl(Map.of(
                SentinelConstants.CORE_KEY_POSTGRES_HOST, "db.internal",
                SentinelConstants.CORE_KEY_POSTGRES_PORT, "5433",
                SentinelConstants.CORE_KEY_POSTGRES_DB, "appdb"));
        assertEquals(SentinelConstants.JDBC_PREFIX + "db.internal:5433/appdb", url);
    }

    @Test
    void resolveJdbcUrlUsesExplicitJdbcUrlWhenSet() {
        String url = Database.resolveJdbcUrl(Map.of(
                SentinelConstants.CORE_KEY_JDBC_URL,
                "jdbc:postgresql://custom:5432/x"));
        assertEquals("jdbc:postgresql://custom:5432/x", url);
    }
}
