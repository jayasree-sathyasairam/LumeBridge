package com.lumebridge.plugin;

import com.google.gson.JsonParser;
import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.MiddlewareFunc;
import com.lumebridge.pipeline.Plugin;
import com.lumebridge.pipeline.Stage;
import com.lumebridge.util.JsonBody;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Sets {@link com.lumebridge.pipeline.RequestContext#setRoute(String)} from JSON-RPC / MCP hints or REST default.
 */
public class DualModeRouterPlugin implements Plugin {

    @Override public String name() { return SentinelConstants.PLUGIN_DUAL_MODE_ROUTER; }
    @Override public Stage stage() { return Stage.ROUTE; }
    @Override public int order() { return 1; }

    @Override
    public MiddlewareFunc middleware() {
        return (ctx, next) -> {
            String route = SentinelConstants.ROUTE_REST;
            String mcpHeader = ctx.getRequestHeader(SentinelConstants.HEADER_MCP_PROTOCOL_VERSION);
            if (mcpHeader != null && !mcpHeader.isBlank()) {
                route = SentinelConstants.ROUTE_MCP;
            } else {
                try {
                    var body = JsonBody.tryParse(ctx.getRawPayload());
                    if (body != null && body.has(SentinelConstants.JSON_FIELD_JSONRPC)
                            && SentinelConstants.JSON_JSONRPC_VERSION.equals(body.get(SentinelConstants.JSON_FIELD_JSONRPC).getAsString())) {
                        route = SentinelConstants.ROUTE_MCP;
                    } else {
                        String raw = new String(ctx.getRawPayload(), StandardCharsets.UTF_8).trim();
                        if (raw.startsWith("{")) {
                            var el = JsonParser.parseString(raw);
                            if (el.isJsonObject() && el.getAsJsonObject().has(SentinelConstants.JSON_FIELD_JSONRPC)) {
                                route = SentinelConstants.ROUTE_MCP;
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // keep REST
                }
            }
            ctx.setRoute(route);
            ctx.getMetadata().put(SentinelConstants.META_ROUTE, route);
            next.run();
        };
    }
}
