package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.MiddlewareFunc;
import com.lumebridge.pipeline.Plugin;
import com.lumebridge.pipeline.Stage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tracks lock contention and hold durations across all requests.
 * Exposes metrics via internal counters.
 */
public class LockMetricsPlugin implements Plugin {

    private static final Map<String, AtomicLong> COUNTERS = new ConcurrentHashMap<>();

    @Override public String name() { return SentinelConstants.PLUGIN_LOCK_METRICS; }
    @Override public Stage stage() { return Stage.POST_PROCESS; }
    @Override public int order() { return 2; }

    @Override
    public MiddlewareFunc middleware() {
        return (ctx, next) -> {
            // Metrics are recorded by DistributedLockPlugin into ctx.metrics
            // This plugin aggregates them for the metrics exporter
            Long waitTime = ctx.getMetrics().get("lock_acquire_wait_ms");
            if (waitTime != null) {
                increment("lock_total_wait_ms", waitTime);
                increment("lock_acquire_attempts", 1);
            }
            
            if (SentinelConstants.STATUS_LOCKED.equalsIgnoreCase(ctx.getStatus())) {
                increment("lock_contention_count", 1);
            }

            next.run();
        };
    }

    private void increment(String name, long val) {
        COUNTERS.computeIfAbsent(name, k -> new AtomicLong()).addAndGet(val);
    }

    public static Map<String, Long> getGlobalMetrics() {
        Map<String, Long> out = new java.util.LinkedHashMap<>();
        COUNTERS.forEach((k, v) -> out.put(k, v.get()));
        return out;
    }
}
