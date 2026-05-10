package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.RequestContext;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IntelligentRouterPluginTest {

    @Test
    void routesBasedOnSize() throws Exception {
        IntelligentRouterPlugin router = new IntelligentRouterPlugin();
        
        // 1. Small payload -> gpt-4o-mini
        RequestContext ctxSmall = new RequestContext("short prompt".getBytes(StandardCharsets.UTF_8));
        router.middleware().apply(ctxSmall, () -> {});
        assertEquals("gpt-4o-mini", ctxSmall.getMetadata().get(SentinelConstants.META_ROUTE));
        assertEquals("claude-3-haiku", ctxSmall.getMetadata().get("fallback_route"));

        // 2. Large payload -> gpt-4-turbo
        StringBuilder large = new StringBuilder();
        for (int i = 0; i < 6000; i++) large.append("a");
        RequestContext ctxLarge = new RequestContext(large.toString().getBytes(StandardCharsets.UTF_8));
        router.middleware().apply(ctxLarge, () -> {});
        assertEquals("gpt-4-turbo", ctxLarge.getMetadata().get(SentinelConstants.META_ROUTE));
    }
}
