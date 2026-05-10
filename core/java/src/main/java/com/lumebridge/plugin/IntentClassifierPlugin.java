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
            String intent = SentinelConstants.INTENT_CACHE_ELIGIBLE;
            String eligible = SentinelConstants.VAL_TRUE;
            var body = JsonBody.tryParse(ctx.getRawPayload());
            if (body != null && body.has(SentinelConstants.JSON_FIELD_CACHE_BYPASS)
                    && !body.get(SentinelConstants.JSON_FIELD_CACHE_BYPASS).isJsonNull()
                    && body.get(SentinelConstants.JSON_FIELD_CACHE_BYPASS).getAsBoolean()) {
                intent = SentinelConstants.INTENT_MUST_EXECUTE;
                eligible = SentinelConstants.VAL_FALSE;
            }
            ctx.getMetadata().put(SentinelConstants.META_INTENT, intent);
            ctx.getMetadata().put(SentinelConstants.META_INTENT_CACHE_ELIGIBLE, eligible);
            next.run();
        };
    }
}
