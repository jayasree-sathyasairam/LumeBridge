package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.RequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.JedisPooled;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NonceOrderingPluginTest {

    private NonceOrderingPlugin plugin;
    private JedisPooled mockJedis;

    @BeforeEach
    void setUp() {
        plugin = new NonceOrderingPlugin();
        mockJedis = mock(JedisPooled.class);
        try {
            java.lang.reflect.Field field = NonceOrderingPlugin.class.getDeclaredField("jedis");
            field.setAccessible(true);
            field.set(plugin, mockJedis);
        } catch (Exception e) {
            fail("Failed to inject mock jedis");
        }
    }

    @Test
    void acceptsIncreasingNonce() throws Exception {
        String raw = "{\"nonce\": 100}";
        RequestContext ctx = new RequestContext(raw.getBytes(StandardCharsets.UTF_8));
        ctx.setPayloadHash("hash1");
        
        when(mockJedis.get(anyString())).thenReturn("99");

        plugin.middleware().apply(ctx, () -> {});

        assertNull(ctx.getError());
        verify(mockJedis).set(eq("nonce:hash1"), eq("100"), any());
    }

    @Test
    void rejectsLowerNonce() throws Exception {
        String raw = "{\"nonce\": 50}";
        RequestContext ctx = new RequestContext(raw.getBytes(StandardCharsets.UTF_8));
        ctx.setPayloadHash("hash1");
        
        when(mockJedis.get(anyString())).thenReturn("100");

        plugin.middleware().apply(ctx, () -> {});

        assertEquals(SentinelConstants.HTTP_STATUS_CONFLICT, ctx.getHttpStatus());
        assertEquals(SentinelConstants.STATUS_NONCE_REJECTED, ctx.getStatus());
    }

    @Test
    void proceedsIfNoNonceInPayload() throws Exception {
        String raw = "{}";
        RequestContext ctx = new RequestContext(raw.getBytes(StandardCharsets.UTF_8));
        
        plugin.middleware().apply(ctx, () -> {});

        assertNull(ctx.getError());
        verify(mockJedis, never()).get(anyString());
    }
}
