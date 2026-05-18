package com.lumebridge.plugin;

import com.google.gson.JsonElement;
import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.MiddlewareFunc;
import com.lumebridge.pipeline.Plugin;
import com.lumebridge.pipeline.Stage;
import com.lumebridge.util.JsonBody;

import java.nio.charset.StandardCharsets;

/**
 * Detects route type: REST or JSON-RPC/MCP based on headers and payload.
 * Reduces cyclomatic complexity by extracting detection logic.
 */
public class DualModeRouterPlugin implements Plugin {

    @Override public String name() { return SentinelConstants.PLUGIN_DUAL_MODE_ROUTER; }
    @Override public Stage stage() { return Stage.ROUTE; }
    @Override public int order() { return 1; }

    @Override
    public MiddlewareFunc middleware() {
        return (ctx, next) -> {
            String route = detectRoute(ctx);
            ctx.setRoute(route);
            ctx.getMetadata().put(SentinelConstants.META_PROTOCOL_ROUTE, route);
            ctx.getMetadata().put(SentinelConstants.META_ROUTE, route);
            next.run();
        };
    }

    private String detectRoute(com.lumebridge.pipeline.RequestContext ctx) {
        if (hasMcpHeader(ctx)) {
            return SentinelConstants.ROUTE_MCP;
        }
        if (isJsonRpcPayload(ctx.getRawPayload())) {
            return SentinelConstants.ROUTE_MCP;
        }
        return SentinelConstants.ROUTE_REST;
    }

    private boolean hasMcpHeader(com.lumebridge.pipeline.RequestContext ctx) {
        String header = ctx.getRequestHeader(SentinelConstants.HEADER_MCP_PROTOCOL_VERSION);
        return isNotEmpty(header);
    }

    private boolean isNotEmpty(String value) {
        return value != null && !value.isEmpty() && !value.isBlank();
    }

    private boolean isJsonRpcPayload(byte[] raw) {
        try {
            var body = JsonBody.tryParse(raw);
            if (isValidJsonBody(body) && hasJsonRpcVersion(body)) {
                return true;
            }
            return parseRawJson(raw);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isValidJsonBody(JsonElement body) {
        return body != null && body.isJsonObject();
    }

    private boolean hasJsonRpcVersion(JsonElement body) {
        if (!isValidJsonBody(body)) {
            return false;
        }
        var obj = body.getAsJsonObject();
        if (!obj.has(SentinelConstants.JSON_FIELD_JSONRPC)) {
            return false;
        }
        String version = obj.get(SentinelConstants.JSON_FIELD_JSONRPC).getAsString();
        return SentinelConstants.JSON_JSONRPC_VERSION.equals(version);
    }

    private boolean parseRawJson(byte[] raw) {
        String text = new String(raw, StandardCharsets.UTF_8).trim();
        if (isEmpty(text) || !text.startsWith("{")) {
            return false;
        }
        try {
            var el = com.google.gson.JsonParser.parseString(text);
            return el.isJsonObject() && el.getAsJsonObject().has(SentinelConstants.JSON_FIELD_JSONRPC);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isEmpty(String text) {
        return text == null || text.isEmpty();
    }
}
