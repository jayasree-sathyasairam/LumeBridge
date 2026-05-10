package com.lumebridge.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequestContextTest {

    @Test
    void putRequestHeaderKeepsFirstValueCaseInsensitive() {
        RequestContext ctx = new RequestContext("{}".getBytes());
        ctx.putRequestHeader("MCP-Protocol-Version", "2024-11-05");
        ctx.putRequestHeader("mcp-protocol-version", "ignored");
        assertEquals("2024-11-05", ctx.getRequestHeader("MCP-Protocol-Version"));
        assertEquals(1, ctx.getRequestHeaders().size());
    }

    @Test
    void nullHeaderIgnored() {
        RequestContext ctx = new RequestContext("{}".getBytes());
        ctx.putRequestHeader(null, "x");
        ctx.putRequestHeader("X", null);
        assertNull(ctx.getRequestHeader("X"));
    }
}
