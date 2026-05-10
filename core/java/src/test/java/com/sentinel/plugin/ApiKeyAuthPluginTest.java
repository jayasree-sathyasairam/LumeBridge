package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.RequestContext;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ApiKeyAuthPluginTest {

    @Test
    void validatesValidKey() throws Exception {
        ApiKeyAuthPlugin auth = new ApiKeyAuthPlugin();
        RequestContext ctx = new RequestContext("{}".getBytes(StandardCharsets.UTF_8));
        ctx.putRequestHeader("X-API-Key", "sk-sentinel-user123");

        auth.middleware().apply(ctx, () -> {});

        assertNull(ctx.getError());
        assertEquals("user123", ctx.getMetadata().get(SentinelConstants.META_AUTH_USER_ID));
    }

    @Test
    void rejectsMissingKey() throws Exception {
        ApiKeyAuthPlugin auth = new ApiKeyAuthPlugin();
        RequestContext ctx = new RequestContext("{}".getBytes(StandardCharsets.UTF_8));

        auth.middleware().apply(ctx, () -> {});

        assertEquals(401, ctx.getHttpStatus());
        assertTrue(ctx.getError().getMessage().contains("Missing API Key"));
    }

    @Test
    void rejectsInvalidKey() throws Exception {
        ApiKeyAuthPlugin auth = new ApiKeyAuthPlugin();
        RequestContext ctx = new RequestContext("{}".getBytes(StandardCharsets.UTF_8));
        ctx.putRequestHeader("X-API-Key", "invalid-key");

        auth.middleware().apply(ctx, () -> {});

        assertEquals(401, ctx.getHttpStatus());
        assertTrue(ctx.getError().getMessage().contains("Invalid API Key"));
    }
}
