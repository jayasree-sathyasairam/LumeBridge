package com.sentinel.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.db.SemanticCacheRepository;
import com.lumebridge.pipeline.RequestContext;
import com.lumebridge.plugin.SemanticCachePlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SemanticCachePluginTest {

    @Mock
    SemanticCacheRepository repository;

    @Test
    void populatesMetadataOnHit() throws Exception {
        when(repository.embeddingDimensions()).thenReturn(64);
        when(repository.findClosest(any(), anyDouble(), anyString()))
                .thenReturn(Optional.of(new SemanticCacheRepository.CacheHit("k1", "{\"cached\":true}", 0.99)));
        SemanticCachePlugin p = new SemanticCachePlugin(repository);
        p.init(Map.of());
        RequestContext ctx = new RequestContext("{\"prompt\":\"hello\",\"nonce\":1}".getBytes());
        ctx.putRequestHeader(SentinelConstants.HEADER_API_KEY, "sk-sentinel-u1");
        p.middleware().apply(ctx, () -> { });
        assertEquals(SentinelConstants.VAL_TRUE, ctx.getMetadata().get(SentinelConstants.META_SEMANTIC_CACHE_HIT));
        assertTrue(ctx.getMetadata().containsKey(SentinelConstants.META_SEMANTIC_CACHE_SIMILARITY));
        assertEquals("k1", ctx.getMetadata().get(SentinelConstants.META_SEMANTIC_CACHE_KEY));
    }

    @Test
    void skipsLookupWhenIntentNotCacheEligible() throws Exception {
        SemanticCachePlugin p = new SemanticCachePlugin(repository);
        p.init(Map.of());
        RequestContext ctx = new RequestContext("{\"prompt\":\"hello\"}".getBytes());
        ctx.getMetadata().put(SentinelConstants.META_INTENT_CACHE_ELIGIBLE, SentinelConstants.VAL_FALSE);
        ctx.putRequestHeader(SentinelConstants.HEADER_API_KEY, "k");
        p.middleware().apply(ctx, () -> { });
        verify(repository, never()).findClosest(any(), anyDouble(), anyString());
    }

    @Test
    void skipsLookupWhenRepositoryNull() throws Exception {
        SemanticCachePlugin p = new SemanticCachePlugin(null);
        p.init(Map.of());
        AtomicBoolean ran = new AtomicBoolean();
        RequestContext ctx = new RequestContext("{}".getBytes());
        p.middleware().apply(ctx, () -> ran.set(true));
        assertTrue(ran.get());
    }
}
