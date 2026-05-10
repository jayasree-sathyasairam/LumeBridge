package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.RequestContext;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayloadNormalizerPluginTest {

    @Test
    void sortsTopLevelKeys() throws Exception {
        PayloadNormalizerPlugin n = new PayloadNormalizerPlugin();
        n.init(java.util.Map.of());
        RequestContext ctx = new RequestContext("{\"z\":1,\"a\":2}".getBytes(StandardCharsets.UTF_8));
        n.middleware().apply(ctx, () -> { });
        String out = new String(ctx.getRawPayload(), StandardCharsets.UTF_8);
        assertTrue(out.indexOf("\"a\"") < out.indexOf("\"z\""));
    }

    @Test
    void malformedJsonAddsWarning() throws Exception {
        PayloadNormalizerPlugin n = new PayloadNormalizerPlugin();
        n.init(java.util.Map.of());
        byte[] raw = "{not-valid-json".getBytes(StandardCharsets.UTF_8);
        RequestContext ctx = new RequestContext(raw);
        n.middleware().apply(ctx, () -> { });
        assertEquals("{not-valid-json", new String(ctx.getRawPayload(), StandardCharsets.UTF_8));
        assertTrue(ctx.getWarnings().stream().anyMatch(w -> w.startsWith(SentinelConstants.WARN_PAYLOAD_NORMALIZER_PREFIX)));
    }

    @Test
    void jsonArrayLeavesPayloadUnchangedAndNoWarning() throws Exception {
        PayloadNormalizerPlugin n = new PayloadNormalizerPlugin();
        n.init(java.util.Map.of());
        String body = "[1,2]";
        RequestContext ctx = new RequestContext(body.getBytes(StandardCharsets.UTF_8));
        n.middleware().apply(ctx, () -> { });
        assertEquals(body, new String(ctx.getRawPayload(), StandardCharsets.UTF_8));
        assertTrue(ctx.getWarnings().isEmpty());
    }
}
