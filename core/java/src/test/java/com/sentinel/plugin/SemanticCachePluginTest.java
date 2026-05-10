package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.db.SemanticCacheRepository;
import com.lumebridge.pipeline.RequestContext;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SemanticCachePluginTest {

    @Mock
    SemanticCacheRepository repository;

    @Test
    void populatesMetadataOnHit() throws Exception {
        when(repository.findClosest(any(), anyDouble()))
                .thenReturn(Optional.of(new SemanticCacheRepository.CacheHit("k1", "{\"cached\":true}", 0.99)));
        SemanticCachePlugin p = new SemanticCachePlugin(repository);
        p.init(Map.of());
        RequestContext ctx = new RequestContext("any".getBytes());
        p.middleware().apply(ctx, () -> { });
        assertEquals(SentinelConstants.VAL_TRUE, ctx.getMetadata().get(SentinelConstants.META_SEMANTIC_CACHE_HIT));
        assertTrue(ctx.getMetadata().containsKey(SentinelConstants.META_SEMANTIC_CACHE_SIMILARITY));
        assertEquals("k1", ctx.getMetadata().get(SentinelConstants.META_SEMANTIC_CACHE_KEY));
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
