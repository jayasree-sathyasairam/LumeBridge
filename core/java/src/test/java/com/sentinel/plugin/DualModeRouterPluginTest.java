package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.RequestContext;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DualModeRouterPluginTest {

    @Test
    void detectsMcpFromJsonRpcBody() throws Exception {
        DualModeRouterPlugin r = new DualModeRouterPlugin();
        r.init(java.util.Map.of());
        String body = "{\"jsonrpc\":\"2.0\",\"method\":\"ping\"}";
        RequestContext ctx = new RequestContext(body.getBytes(StandardCharsets.UTF_8));
        r.middleware().apply(ctx, () -> { });
        assertEquals(SentinelConstants.ROUTE_MCP, ctx.getRoute());
        assertEquals(SentinelConstants.ROUTE_MCP, ctx.getMetadata().get(SentinelConstants.META_ROUTE));
    }

    @Test
    void defaultsToRest() throws Exception {
        DualModeRouterPlugin r = new DualModeRouterPlugin();
        r.init(java.util.Map.of());
        RequestContext ctx = new RequestContext("{\"hello\":1}".getBytes(StandardCharsets.UTF_8));
        r.middleware().apply(ctx, () -> { });
        assertEquals(SentinelConstants.ROUTE_REST, ctx.getRoute());
    }

    @Test
    void detectsMcpFromHeader() throws Exception {
        DualModeRouterPlugin r = new DualModeRouterPlugin();
        r.init(java.util.Map.of());
        RequestContext ctx = new RequestContext("{}".getBytes(StandardCharsets.UTF_8));
        ctx.putRequestHeader(SentinelConstants.HEADER_MCP_PROTOCOL_VERSION, "2024-11-05");
        r.middleware().apply(ctx, () -> { });
        assertEquals(SentinelConstants.ROUTE_MCP, ctx.getRoute());
    }
}
