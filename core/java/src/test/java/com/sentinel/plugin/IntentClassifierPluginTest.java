package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.RequestContext;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IntentClassifierPluginTest {

    @Test
    void cacheBypassMarksMustExecute() throws Exception {
        IntentClassifierPlugin ic = new IntentClassifierPlugin();
        ic.init(java.util.Map.of());
        String body = "{\"cache_bypass\":true}";
        RequestContext ctx = new RequestContext(body.getBytes(StandardCharsets.UTF_8));
        ic.middleware().apply(ctx, () -> { });
        assertEquals(SentinelConstants.INTENT_MUST_EXECUTE, ctx.getMetadata().get(SentinelConstants.META_INTENT));
        assertEquals(SentinelConstants.VAL_FALSE, ctx.getMetadata().get(SentinelConstants.META_INTENT_CACHE_ELIGIBLE));
    }

    @Test
    void defaultCacheEligible() throws Exception {
        IntentClassifierPlugin ic = new IntentClassifierPlugin();
        ic.init(java.util.Map.of());
        RequestContext ctx = new RequestContext("{}".getBytes(StandardCharsets.UTF_8));
        ic.middleware().apply(ctx, () -> { });
        assertEquals(SentinelConstants.INTENT_CACHE_ELIGIBLE, ctx.getMetadata().get(SentinelConstants.META_INTENT));
        assertEquals(SentinelConstants.VAL_TRUE, ctx.getMetadata().get(SentinelConstants.META_INTENT_CACHE_ELIGIBLE));
    }

    @Test
    void explicitCacheBypassFalseIsEligible() throws Exception {
        IntentClassifierPlugin ic = new IntentClassifierPlugin();
        ic.init(java.util.Map.of());
        RequestContext ctx = new RequestContext("{\"cache_bypass\":false}".getBytes(StandardCharsets.UTF_8));
        ic.middleware().apply(ctx, () -> { });
        assertEquals(SentinelConstants.INTENT_CACHE_ELIGIBLE, ctx.getMetadata().get(SentinelConstants.META_INTENT));
        assertEquals(SentinelConstants.VAL_TRUE, ctx.getMetadata().get(SentinelConstants.META_INTENT_CACHE_ELIGIBLE));
    }
}
