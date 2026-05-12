package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.MiddlewareFunc;
import com.lumebridge.pipeline.Plugin;
import com.lumebridge.pipeline.Stage;
import com.lumebridge.util.JsonBody;

import java.util.Map;

/**
 * Marks cache-eligible vs must-execute traffic using payload {@code cache_bypass} (default: cache-eligible).
 */
public class IntentClassifierPlugin implements Plugin {

    @Override public String name() { return SentinelConstants.PLUGIN_INTENT_CLASSIFIER; }
    @Override public Stage stage() { return Stage.ROUTE; }
    @Override public int order() { return 3; }

    @Override
    public void init(Map<String, String> config) {}

    @Override
    public MiddlewareFunc middleware() {
        return (ctx, next) -> {
            String[] intent = { SentinelConstants.INTENT_CACHE_ELIGIBLE, SentinelConstants.VAL_TRUE };

            var body = JsonBody.tryParse(ctx.getRawPayload());
            if (shouldBypassCache(body)) {
                intent[0] = SentinelConstants.INTENT_MUST_EXECUTE;
                intent[1] = SentinelConstants.VAL_FALSE;
            }

            ctx.getMetadata().put(SentinelConstants.META_INTENT, intent[0]);
            ctx.getMetadata().put(SentinelConstants.META_INTENT_CACHE_ELIGIBLE, intent[1]);
            next.run();
        };
    }

    private boolean shouldBypassCache(com.google.gson.JsonElement body) {
        if (!isValidJsonObject(body)) {
            return false;
        }
        var obj = body.getAsJsonObject();
        return hasBypassFlag(obj);
    }

    private boolean isValidJsonObject(com.google.gson.JsonElement body) {
        return body != null && body.isJsonObject();
    }

    private boolean hasBypassFlag(com.google.gson.JsonObject obj) {
        return obj.has(SentinelConstants.JSON_FIELD_CACHE_BYPASS)
                && !obj.get(SentinelConstants.JSON_FIELD_CACHE_BYPASS).isJsonNull()
                && obj.get(SentinelConstants.JSON_FIELD_CACHE_BYPASS).getAsBoolean();
    }
}
