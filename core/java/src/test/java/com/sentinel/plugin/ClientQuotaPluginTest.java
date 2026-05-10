package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.RequestContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.JedisPooled;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClientQuotaPluginTest {

    private ClientQuotaPlugin quotaPlugin;
    private JedisPooled mockJedis;

    @BeforeEach
    void setUp() {
        quotaPlugin = new ClientQuotaPlugin();
        mockJedis = mock(JedisPooled.class);
        // Inject mock jedis using reflection since it's private and no setter
        try {
            java.lang.reflect.Field field = ClientQuotaPlugin.class.getDeclaredField("jedis");
            field.setAccessible(true);
            field.set(quotaPlugin, mockJedis);
        } catch (Exception e) {
            fail("Failed to inject mock jedis");
        }
    }

    @Test
    void allowsRequestUnderLimit() throws Exception {
        RequestContext ctx = new RequestContext("{}".getBytes(StandardCharsets.UTF_8));
        ctx.getMetadata().put(SentinelConstants.META_AUTH_USER_ID, "user1");
        
        when(mockJedis.incr(anyString())).thenReturn(10L);

        quotaPlugin.middleware().apply(ctx, () -> {});

        assertNull(ctx.getError());
    }

    @Test
    void blocksRequestOverLimit() throws Exception {
        RequestContext ctx = new RequestContext("{}".getBytes(StandardCharsets.UTF_8));
        ctx.getMetadata().put(SentinelConstants.META_AUTH_USER_ID, "user1");
        
        when(mockJedis.incr(anyString())).thenReturn(61L);

        quotaPlugin.middleware().apply(ctx, () -> {});

        assertEquals(429, ctx.getHttpStatus());
        assertTrue(ctx.getError().getMessage().contains("Rate limit exceeded"));
    }

    @Test
    void setsExpiryOnFirstRequest() throws Exception {
        RequestContext ctx = new RequestContext("{}".getBytes(StandardCharsets.UTF_8));
        ctx.getMetadata().put(SentinelConstants.META_AUTH_USER_ID, "user1");
        
        when(mockJedis.incr(anyString())).thenReturn(1L);

        quotaPlugin.middleware().apply(ctx, () -> {});

        verify(mockJedis).expire(anyString(), eq(60L));
    }
}
