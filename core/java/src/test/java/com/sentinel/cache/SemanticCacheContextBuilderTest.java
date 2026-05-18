package com.sentinel.cache;

import com.lumebridge.cache.SemanticCacheContextBuilder;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class SemanticCacheContextBuilderTest {

    @Test
    void freshnessSeparatesTodayVersusYesterdayPrompts() {
        byte[] today = "{\"prompt\":\"What's the weather in NYC today?\",\"nonce\":1}".getBytes(StandardCharsets.UTF_8);
        byte[] yesterday = "{\"prompt\":\"What was the weather in NYC yesterday?\",\"nonce\":2}".getBytes(StandardCharsets.UTF_8);
        assertEquals("REALTIME_OR_TODAY", SemanticCacheContextBuilder.freshnessBucket(today));
        assertEquals("RELATIVE_YESTERDAY", SemanticCacheContextBuilder.freshnessBucket(yesterday));
        assertNotEquals(
                SemanticCacheContextBuilder.cacheScope("tenant-a", SemanticCacheContextBuilder.freshnessBucket(today)),
                SemanticCacheContextBuilder.cacheScope("tenant-a", SemanticCacheContextBuilder.freshnessBucket(yesterday)));
    }

    @Test
    void isoDateUsesLiteralBucket() {
        byte[] dated = "{\"prompt\":\"Rain on 2026-05-01 in Seattle\",\"nonce\":1}".getBytes(StandardCharsets.UTF_8);
        assertEquals("DATE_LITERAL", SemanticCacheContextBuilder.freshnessBucket(dated));
    }

    @Test
    void intentBucketsAreStable() {
        assertEquals("INTENT_STATIC", SemanticCacheContextBuilder.freshnessBucketForIntent(com.lumebridge.intent.QueryIntent.STATIC));
        assertEquals("INTENT_NO_CACHE", SemanticCacheContextBuilder.freshnessBucketForIntent(com.lumebridge.intent.QueryIntent.CONVERSATION));
    }
}
