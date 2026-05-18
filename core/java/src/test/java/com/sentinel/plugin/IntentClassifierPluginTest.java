package com.sentinel.plugin;

import com.lumebridge.plugin.IntentClassifierPlugin;
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
    void defaultEmptyBodyIsConversationAndNotCacheEligible() throws Exception {
        IntentClassifierPlugin ic = new IntentClassifierPlugin();
        ic.init(java.util.Map.of());
        RequestContext ctx = new RequestContext("{}".getBytes(StandardCharsets.UTF_8));
        ic.middleware().apply(ctx, () -> { });
        assertEquals("CONVERSATION", ctx.getMetadata().get(SentinelConstants.META_QUERY_INTENT));
        assertEquals(SentinelConstants.INTENT_MUST_EXECUTE, ctx.getMetadata().get(SentinelConstants.META_INTENT));
        assertEquals(SentinelConstants.VAL_FALSE, ctx.getMetadata().get(SentinelConstants.META_INTENT_CACHE_ELIGIBLE));
    }

    @Test
    void explicitCacheBypassFalseLeavesConversationIneligible() throws Exception {
        IntentClassifierPlugin ic = new IntentClassifierPlugin();
        ic.init(java.util.Map.of());
        RequestContext ctx = new RequestContext("{\"cache_bypass\":false}".getBytes(StandardCharsets.UTF_8));
        ic.middleware().apply(ctx, () -> { });
        assertEquals(SentinelConstants.INTENT_MUST_EXECUTE, ctx.getMetadata().get(SentinelConstants.META_INTENT));
        assertEquals(SentinelConstants.VAL_FALSE, ctx.getMetadata().get(SentinelConstants.META_INTENT_CACHE_ELIGIBLE));
    }

    @Test
    void staticFactualPromptIsCacheEligible() throws Exception {
        IntentClassifierPlugin ic = new IntentClassifierPlugin();
        ic.init(java.util.Map.of());
        RequestContext ctx = new RequestContext(
                "{\"prompt\":\"What is the capital of France?\",\"nonce\":1}".getBytes(StandardCharsets.UTF_8));
        ic.middleware().apply(ctx, () -> { });
        assertEquals("STATIC", ctx.getMetadata().get(SentinelConstants.META_QUERY_INTENT));
        assertEquals(SentinelConstants.INTENT_CACHE_ELIGIBLE, ctx.getMetadata().get(SentinelConstants.META_INTENT));
        assertEquals(SentinelConstants.VAL_TRUE, ctx.getMetadata().get(SentinelConstants.META_INTENT_CACHE_ELIGIBLE));
    }

    @Test
    void jsonQueryIntentOverride() throws Exception {
        IntentClassifierPlugin ic = new IntentClassifierPlugin();
        ic.init(java.util.Map.of());
        RequestContext ctx = new RequestContext(
                "{\"query_intent\":\"TEMPORAL\",\"nonce\":1}".getBytes(StandardCharsets.UTF_8));
        ic.middleware().apply(ctx, () -> { });
        assertEquals("TEMPORAL", ctx.getMetadata().get(SentinelConstants.META_QUERY_INTENT));
        assertEquals(SentinelConstants.INTENT_CACHE_ELIGIBLE, ctx.getMetadata().get(SentinelConstants.META_INTENT));
    }
}
