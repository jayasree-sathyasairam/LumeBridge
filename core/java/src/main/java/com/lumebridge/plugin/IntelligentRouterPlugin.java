package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.MiddlewareFunc;
import com.lumebridge.pipeline.Plugin;
import com.lumebridge.pipeline.Stage;

import java.util.Map;

/**
 * Intelligently routes traffic to different models based on complexity and cost.
 * Also sets fallback models for resilience.
 */
public class IntelligentRouterPlugin implements Plugin {

    @Override public String name() { return SentinelConstants.PLUGIN_INTELLIGENT_ROUTER; }
    @Override public Stage stage() { return Stage.ROUTE; }
    @Override public int order() { return 4; } // After dual-mode and semantic cache

    @Override
    public MiddlewareFunc middleware() {
        return (ctx, next) -> {
            String currentRoute = ctx.getMetadata().get(SentinelConstants.META_ROUTE);
            
            // Logic: If prompt is very long, use a high-capacity model
            byte[] raw = ctx.getRawPayload();
            if (raw != null && raw.length > 5000) {
                ctx.getMetadata().put(SentinelConstants.META_ROUTE, "gpt-4-turbo");
            } else if (currentRoute == null) {
                ctx.getMetadata().put(SentinelConstants.META_ROUTE, "gpt-4o-mini"); // Default cheap model
            }

            // Set a fallback route for SmartRetryPlugin to use if primary fails
            ctx.getMetadata().put("fallback_route", "claude-3-haiku");
            
            next.run();
        };
    }
}
