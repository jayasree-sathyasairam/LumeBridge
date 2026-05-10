package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.db.TaskRepository;
import com.lumebridge.pipeline.MiddlewareFunc;
import com.lumebridge.pipeline.Plugin;
import com.lumebridge.pipeline.Stage;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Background plugin that periodically purges stale tasks from the database.
 */
public class StaleDataCleanerPlugin implements Plugin {

    private TaskRepository repository;
    private ScheduledExecutorService scheduler;
    private int maxAgeHours = 24;
    private int batchLimit = 1000;
    private int intervalMinutes = 60;

    @Override public String name() { return SentinelConstants.PLUGIN_STALE_DATA_CLEANER; }
    @Override public Stage stage() { return Stage.BACKGROUND; }
    @Override public int order() { return 1; }

    @Override
    public void init(Map<String, String> config) {
        this.maxAgeHours = Integer.parseInt(config.getOrDefault("max_age_hours", "24"));
        this.batchLimit = Integer.parseInt(config.getOrDefault("batch_limit", "1000"));
        this.intervalMinutes = Integer.parseInt(config.getOrDefault("interval_minutes", "60"));

        String jdbcUrl = config.get(SentinelConstants.CORE_KEY_JDBC_URL);
        if (jdbcUrl != null) {
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl(jdbcUrl);
            hikariConfig.setUsername(config.get(SentinelConstants.CORE_KEY_POSTGRES_USER));
            hikariConfig.setPassword(config.get(SentinelConstants.CORE_KEY_POSTGRES_PASSWORD));
            this.repository = new TaskRepository(new HikariDataSource(hikariConfig));
        }

        this.scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());
        this.scheduler.scheduleAtFixedRate(this::clean, 1, intervalMinutes, TimeUnit.MINUTES);
    }

    private void clean() {
        if (repository == null) return;
        try {
            int deleted = repository.deleteStale(maxAgeHours, batchLimit);
            if (deleted > 0) {
                System.out.println("[StaleDataCleaner] Purged " + deleted + " stale tasks.");
            }
        } catch (SQLException e) {
            System.err.println("[StaleDataCleaner] Error: " + e.getMessage());
        }
    }

    @Override
    public MiddlewareFunc middleware() {
        return (ctx, next) -> next.run(); // No-op in the request path
    }

    @Override
    public void close() {
        if (scheduler != null) scheduler.shutdown();
    }
}
