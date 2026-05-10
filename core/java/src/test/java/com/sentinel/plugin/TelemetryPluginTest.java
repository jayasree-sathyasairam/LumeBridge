package com.lumebridge.plugin;

import com.lumebridge.pipeline.RequestContext;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryPluginTest {

    @Test
    void recordsDuration() throws Exception {
        TelemetryPlugin telemetry = new TelemetryPlugin();
        RequestContext ctx = new RequestContext("{}".getBytes(StandardCharsets.UTF_8));
        
        telemetry.middleware().apply(ctx, () -> {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                // ignore
            }
        });

        Long duration = ctx.getMetrics().get("request_duration_ms");
        assertNotNull(duration);
        assertTrue(duration >= 10);
    }
}
