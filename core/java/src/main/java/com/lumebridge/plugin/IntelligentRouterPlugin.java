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
            String primaryRoute = selectPrimaryRoute(ctx);
            ctx.getMetadata().put(SentinelConstants.META_ROUTE, primaryRoute);

            String fallbackRoute = selectFallbackRoute(primaryRoute);
            ctx.getMetadata().put("fallback_route", fallbackRoute);

            next.run();
        };
    }

    private String selectPrimaryRoute(com.lumebridge.pipeline.RequestContext ctx) {
        String currentRoute = ctx.getMetadata().get(SentinelConstants.META_ROUTE);
        if (isNotEmpty(currentRoute)) {
            return currentRoute;
        }

        byte[] raw = ctx.getRawPayload();
        if (isLargePayload(raw)) {
            return SentinelConstants.ROUTE_GPT4_TURBO;
        }

        return SentinelConstants.ROUTE_GPT4O_MINI;
    }

    private boolean isNotEmpty(String value) {
        return value != null && !value.isEmpty();
    }

    private boolean isLargePayload(byte[] raw) {
        return raw != null && raw.length > SentinelConstants.MAX_PAYLOAD_SIZE;
    }

    private String selectFallbackRoute(String primaryRoute) {
        if (SentinelConstants.ROUTE_GPT4_TURBO.equals(primaryRoute)) {
            return SentinelConstants.ROUTE_GPT4O;
        }
        if (SentinelConstants.ROUTE_GPT4O.equals(primaryRoute)) {
            return SentinelConstants.ROUTE_GPT4O_MINI;
        }
        if (SentinelConstants.ROUTE_GPT4O_MINI.equals(primaryRoute)) {
            return SentinelConstants.ROUTE_CLAUDE_HAIKU;
        }
        return SentinelConstants.ROUTE_CLAUDE_HAIKU;
    }
}
