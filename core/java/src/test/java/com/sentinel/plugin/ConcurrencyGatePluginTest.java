package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.RequestContext;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrencyGatePluginTest {

    @Test
    void recordsWaitAndWorkMetricsWithZeroDelay() throws Exception {
        ConcurrencyGatePlugin gate = new ConcurrencyGatePlugin();
        gate.init(Map.of(
                SentinelConstants.CORE_KEY_PERMITS, "5",
                SentinelConstants.CORE_KEY_DELAY_MS, "0"));
        RequestContext ctx = new RequestContext("{}".getBytes());
        gate.middleware().apply(ctx, () -> { });
        assertTrue(ctx.getMetrics().containsKey(SentinelConstants.METRIC_GATE_WAIT_MS));
        assertTrue(ctx.getMetrics().containsKey(SentinelConstants.METRIC_GATE_WORK_MS));
        assertEquals(1, gate.getTotalProcessed());
    }
}
