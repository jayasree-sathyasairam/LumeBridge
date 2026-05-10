package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.RequestContext;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenMeterPluginTest {

    @Test
    void estimatesTokensCorrectly() throws Exception {
        TokenMeterPlugin meter = new TokenMeterPlugin();
        // 12 chars -> 3 tokens (12/4)
        String input = "123456789012";
        RequestContext ctx = new RequestContext(input.getBytes(StandardCharsets.UTF_8));

        meter.middleware().apply(ctx, () -> {
            // Mock response: 8 chars -> 2 tokens
            ctx.setResult("12345678");
        });

        assertEquals("3", ctx.getMetadata().get(SentinelConstants.META_TOKENS_INPUT));
        assertEquals("2", ctx.getMetadata().get(SentinelConstants.META_TOKENS_OUTPUT));
        assertEquals(3L, ctx.getMetrics().get("tokens_input"));
        assertEquals(2L, ctx.getMetrics().get("tokens_output"));
    }

    @Test
    void handlesNullPayloads() throws Exception {
        TokenMeterPlugin meter = new TokenMeterPlugin();
        RequestContext ctx = new RequestContext(null);

        meter.middleware().apply(ctx, () -> {});

        assertEquals(null, ctx.getMetadata().get(SentinelConstants.META_TOKENS_INPUT));
    }
}
