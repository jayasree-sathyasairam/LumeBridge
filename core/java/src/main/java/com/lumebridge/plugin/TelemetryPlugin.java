package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.MiddlewareFunc;
import com.lumebridge.pipeline.Plugin;
import com.lumebridge.pipeline.Stage;

/**
 * Provides basic request tracing and duration metrics.
 * Acts as a placeholder for a full OpenTelemetry integration.
 */
public class TelemetryPlugin implements Plugin {

    @Override public String name() { return SentinelConstants.PLUGIN_TELEMETRY; }
    @Override public Stage stage() { return Stage.POST_PROCESS; }
    @Override public int order() { return 3; }

    @Override
    public MiddlewareFunc middleware() {
        return (ctx, next) -> {
            long start = System.currentTimeMillis();
            
            next.run();
            
            long duration = System.currentTimeMillis() - start;
            ctx.getMetrics().put("request_duration_ms", duration);
            
            // Log for visibility
            System.out.printf("[Telemetry] Request %s completed in %d ms with status %s%n",
                    ctx.getRequestId(), duration, ctx.getStatus());
        };
    }
}
