package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.MiddlewareFunc;
import com.lumebridge.pipeline.Plugin;
import com.lumebridge.pipeline.Stage;

import java.util.Map;

/**
 * Validates API Keys from the X-API-Key header.
 * In a production scenario, this would check against Redis or a DB.
 */
public class ApiKeyAuthPlugin implements Plugin {

    @Override public String name() { return SentinelConstants.PLUGIN_API_KEY_AUTH; }
    @Override public Stage stage() { return Stage.PRE_PROCESS; }
    @Override public int order() { return 0; } // First plugin in the pipeline

    @Override
    public MiddlewareFunc middleware() {
        return (ctx, next) -> {
            String apiKey = ctx.getRequestHeader(SentinelConstants.HEADER_API_KEY);
            
            if (apiKey == null || apiKey.isBlank()) {
                fail(ctx, "Missing API Key");
                return;
            }

            // Simple demo logic: any key starting with 'sk-sentinel-' is valid
            if (!apiKey.startsWith("sk-sentinel-")) {
                fail(ctx, "Invalid API Key");
                return;
            }

            // Extract "user_id" from key for metadata
            String userId = apiKey.substring("sk-sentinel-".length());
            ctx.getMetadata().put(SentinelConstants.META_AUTH_USER_ID, userId);
            
            next.run();
        };
    }

    private void fail(com.lumebridge.pipeline.RequestContext ctx, String reason) {
        ctx.setHttpStatus(401); // Unauthorized
        ctx.setStatus(SentinelConstants.STATUS_ERROR);
        ctx.setError(new RuntimeException(reason));
    }
}
