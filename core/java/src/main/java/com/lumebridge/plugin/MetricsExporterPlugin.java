package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.MiddlewareFunc;
import com.lumebridge.pipeline.Plugin;
import com.lumebridge.pipeline.Stage;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aggregates metrics from various plugins and exposes them for export.
 */
public class MetricsExporterPlugin implements Plugin {

    @Override public String name() { return SentinelConstants.PLUGIN_METRICS_EXPORTER; }
    @Override public Stage stage() { return Stage.BACKGROUND; }
    @Override public int order() { return 2; }

    @Override
    public MiddlewareFunc middleware() {
        return (ctx, next) -> next.run();
    }

    public Map<String, Object> collectAll() {
        Map<String, Object> all = new LinkedHashMap<>();
        
        // 1. Lock Metrics
        all.put("locks", LockMetricsPlugin.getGlobalMetrics());
        
        // 2. Gateway Stats
        // In a real app, we'd have a central registry (Micrometer/Prometheus)
        return all;
    }
}
