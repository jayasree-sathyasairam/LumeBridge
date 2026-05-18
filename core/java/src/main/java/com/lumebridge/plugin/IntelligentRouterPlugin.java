package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.MiddlewareFunc;
import com.lumebridge.pipeline.Plugin;
import com.lumebridge.pipeline.Stage;
import com.lumebridge.routing.ModelRouteSelector;

/**
 * V2 P1: selects a model tier from complexity + approximate tokens while preserving REST/MCP transport metadata.
 */
public class IntelligentRouterPlugin implements Plugin {

    @Override public String name() { return SentinelConstants.PLUGIN_INTELLIGENT_ROUTER; }
    @Override public Stage stage() { return Stage.ROUTE; }
    @Override public int order() { return 4; }

    @Override
    public MiddlewareFunc middleware() {
        return (ctx, next) -> {
            String protocol = ctx.getMetadata().get(SentinelConstants.META_PROTOCOL_ROUTE);
            if (protocol == null || protocol.isBlank()) {
                protocol = ctx.getMetadata().get(SentinelConstants.META_ROUTE);
            }

            byte[] raw = ctx.getRawPayload();
            boolean payloadVeryLarge = raw != null && raw.length > SentinelConstants.MAX_PAYLOAD_SIZE;
            String prompt = com.lumebridge.cache.SemanticCacheContextBuilder.promptTextFromPayload(raw);
            ModelRouteSelector.Complexity complexity = ModelRouteSelector.detectComplexity(prompt);
            int approxTokens = ModelRouteSelector.approximatePromptTokens(raw);

            String primary = ModelRouteSelector.selectPrimaryRoute(complexity, approxTokens, payloadVeryLarge);

            boolean transportKnown = SentinelConstants.ROUTE_REST.equals(protocol)
                    || SentinelConstants.ROUTE_MCP.equals(protocol);

            if (transportKnown) {
                ctx.getMetadata().put(SentinelConstants.META_PROTOCOL_ROUTE, protocol);
                ctx.getMetadata().put(SentinelConstants.META_MODEL_ROUTE, primary);
                ctx.getMetadata().put(SentinelConstants.META_ROUTE, primary);
            } else if (!isMeaningfulRoute(protocol)) {
                ctx.getMetadata().put(SentinelConstants.META_ROUTE, primary);
            }

            String resolvedPrimary = ctx.getMetadata().get(SentinelConstants.META_ROUTE);
            String fallback = ModelRouteSelector.selectFallbackRoute(resolvedPrimary != null ? resolvedPrimary : primary);
            ctx.getMetadata().put("fallback_route", fallback);

            next.run();
        };
    }

    private static boolean isMeaningfulRoute(String protocol) {
        return protocol != null && !protocol.isBlank();
    }
}
