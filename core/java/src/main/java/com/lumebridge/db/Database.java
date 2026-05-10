package com.lumebridge.db;

import com.lumebridge.SentinelConstants;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.util.Map;

public final class Database {

    private Database() {}

    /**
     * Resolves the JDBC URL from flattened {@code core} settings (same rules as {@link #createDataSource}).
     * Does not open a connection; safe for unit tests.
     */
    public static String resolveJdbcUrl(Map<String, String> core) {
        String jdbcUrl = core.get(SentinelConstants.CORE_KEY_JDBC_URL);
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            String host = core.getOrDefault(SentinelConstants.CORE_KEY_POSTGRES_HOST, SentinelConstants.DEFAULT_POSTGRES_HOST);
            int port = Integer.parseInt(core.getOrDefault(SentinelConstants.CORE_KEY_POSTGRES_PORT, SentinelConstants.DEFAULT_POSTGRES_PORT));
            String db = core.getOrDefault(SentinelConstants.CORE_KEY_POSTGRES_DB, SentinelConstants.DEFAULT_POSTGRES_DB);
            jdbcUrl = SentinelConstants.JDBC_PREFIX + "%s:%d/%s".formatted(host, port, db);
        }
        return jdbcUrl;
    }

    public static HikariDataSource createDataSource(Map<String, String> core) {
        String jdbcUrl = resolveJdbcUrl(core);
        String user = core.getOrDefault(SentinelConstants.CORE_KEY_POSTGRES_USER, SentinelConstants.DEFAULT_POSTGRES_USER);
        String password = core.getOrDefault(SentinelConstants.CORE_KEY_POSTGRES_PASSWORD, SentinelConstants.DEFAULT_POSTGRES_PASSWORD);

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(jdbcUrl);
        cfg.setUsername(user);
        cfg.setPassword(password);
        cfg.setMaximumPoolSize(SentinelConstants.HI_MAX_POOL_SIZE);
        cfg.setPoolName(SentinelConstants.HI_POOL_NAME);
        cfg.addDataSourceProperty(SentinelConstants.HI_CACHE_PREP_STMTS, SentinelConstants.VAL_TRUE);
        return new HikariDataSource(cfg);
    }
}
