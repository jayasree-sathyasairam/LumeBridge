package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.RequestContext;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LockMetricsPluginTest {

    @Test
    void aggregatesMetrics() throws Exception {
        LockMetricsPlugin plugin = new LockMetricsPlugin();
        RequestContext ctx = new RequestContext("{}".getBytes(StandardCharsets.UTF_8));
        
        // Mock a lock wait of 50ms and contention
        ctx.getMetrics().put("lock_acquire_wait_ms", 50L);
        ctx.setStatus(SentinelConstants.STATUS_LOCKED);

        plugin.middleware().apply(ctx, () -> {});

        Map<String, Long> global = LockMetricsPlugin.getGlobalMetrics();
        assertTrue(global.get("lock_total_wait_ms") >= 50);
        assertEquals(1L, global.get("lock_contention_count"));
        assertEquals(1L, global.get("lock_acquire_attempts"));
    }
}
