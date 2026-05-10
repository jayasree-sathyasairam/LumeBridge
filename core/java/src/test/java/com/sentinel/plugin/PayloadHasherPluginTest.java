package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.RequestContext;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PayloadHasherPluginTest {

    @Test
    void setsHashAndRequestId() throws Exception {
        PayloadHasherPlugin h = new PayloadHasherPlugin();
        byte[] raw = "{\"a\":1}".getBytes();
        RequestContext ctx = new RequestContext(raw);
        h.middleware().apply(ctx, () -> { });
        assertNotNull(ctx.getPayloadHash());
        assertEquals(64, ctx.getPayloadHash().length());
        assertNotNull(ctx.getRequestId());
        assertEquals(200, ctx.getHttpStatus());
    }

    @Test
    void emptyPayloadRejected() throws Exception {
        PayloadHasherPlugin h = new PayloadHasherPlugin();
        RequestContext ctx = new RequestContext(new byte[0]);
        h.middleware().apply(ctx, () -> { });
        assertEquals(SentinelConstants.HTTP_STATUS_BAD_REQUEST, ctx.getHttpStatus());
        assertEquals(SentinelConstants.STATUS_ERROR, ctx.getStatus());
    }

    @Test
    void sameBytesSameHash() throws Exception {
        PayloadHasherPlugin h = new PayloadHasherPlugin();
        RequestContext a = new RequestContext("x".getBytes(StandardCharsets.UTF_8));
        RequestContext b = new RequestContext("x".getBytes(StandardCharsets.UTF_8));
        h.middleware().apply(a, () -> { });
        h.middleware().apply(b, () -> { });
        assertEquals(a.getPayloadHash(), b.getPayloadHash());
    }
}
