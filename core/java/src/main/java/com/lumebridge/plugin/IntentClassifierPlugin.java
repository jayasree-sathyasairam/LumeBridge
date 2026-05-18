package com.lumebridge.plugin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lumebridge.SentinelConstants;
import com.lumebridge.cache.SemanticCacheContextBuilder;
import com.lumebridge.intent.QueryIntent;
import com.lumebridge.intent.QueryIntentDetector;
import com.lumebridge.pipeline.MiddlewareFunc;
import com.lumebridge.pipeline.Plugin;
import com.lumebridge.pipeline.Stage;
import com.lumebridge.util.JsonBody;

import java.util.Locale;
import java.util.Map;

/**
 * V2 P1: classifies {@link QueryIntent} from prompt text (patterns + optional JSON {@code query_intent}),
 * drives semantic-cache eligibility, and preserves {@code cache_bypass}.
 */
public class IntentClassifierPlugin implements Plugin {

    @Override public String name() { return SentinelConstants.PLUGIN_INTENT_CLASSIFIER; }
    @Override public Stage stage() { return Stage.ROUTE; }
    @Override public int order() { return 2; }

    @Override
    public void init(Map<String, String> config) {}

    @Override
    public MiddlewareFunc middleware() {
        return (ctx, next) -> {
            byte[] raw = ctx.getRawPayload();
            var body = JsonBody.tryParse(raw);
            String prompt = SemanticCacheContextBuilder.promptTextFromPayload(raw);

            QueryIntent queryIntent = resolveQueryIntent(body, prompt);
            ctx.getMetadata().put(SentinelConstants.META_QUERY_INTENT, queryIntent.name());

            boolean bypass = shouldBypassCache(body);
            boolean eligible = !bypass && semanticCacheEligible(queryIntent);

            if (bypass) {
                ctx.getMetadata().put(SentinelConstants.META_INTENT, SentinelConstants.INTENT_MUST_EXECUTE);
                ctx.getMetadata().put(SentinelConstants.META_INTENT_CACHE_ELIGIBLE, SentinelConstants.VAL_FALSE);
            } else if (eligible) {
                ctx.getMetadata().put(SentinelConstants.META_INTENT, SentinelConstants.INTENT_CACHE_ELIGIBLE);
                ctx.getMetadata().put(SentinelConstants.META_INTENT_CACHE_ELIGIBLE, SentinelConstants.VAL_TRUE);
            } else {
                ctx.getMetadata().put(SentinelConstants.META_INTENT, SentinelConstants.INTENT_MUST_EXECUTE);
                ctx.getMetadata().put(SentinelConstants.META_INTENT_CACHE_ELIGIBLE, SentinelConstants.VAL_FALSE);
            }

            next.run();
        };
    }

    private static QueryIntent resolveQueryIntent(JsonElement body, String prompt) {
        QueryIntent fromJson = tryParseQueryIntentOverride(body);
        if (fromJson != null) {
            return fromJson;
        }
        return QueryIntentDetector.detect(prompt);
    }

    private static QueryIntent tryParseQueryIntentOverride(JsonElement body) {
        if (body == null || !body.isJsonObject()) {
            return null;
        }
        JsonObject obj = body.getAsJsonObject();
        if (!obj.has(SentinelConstants.JSON_FIELD_QUERY_INTENT)
                || obj.get(SentinelConstants.JSON_FIELD_QUERY_INTENT).isJsonNull()) {
            return null;
        }
        JsonElement el = obj.get(SentinelConstants.JSON_FIELD_QUERY_INTENT);
        if (!el.isJsonPrimitive()) {
            return null;
        }
        String raw = el.getAsString().trim().toUpperCase(Locale.ROOT);
        try {
            return QueryIntent.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean semanticCacheEligible(QueryIntent intent) {
        return intent != QueryIntent.CONVERSATION && intent != QueryIntent.COMPUTATION;
    }

    private boolean shouldBypassCache(JsonElement body) {
        if (!isValidJsonObject(body)) {
            return false;
        }
        var obj = body.getAsJsonObject();
        return hasBypassFlag(obj);
    }

    private boolean isValidJsonObject(JsonElement body) {
        return body != null && body.isJsonObject();
    }

    private boolean hasBypassFlag(JsonObject obj) {
        return obj.has(SentinelConstants.JSON_FIELD_CACHE_BYPASS)
                && !obj.get(SentinelConstants.JSON_FIELD_CACHE_BYPASS).isJsonNull()
                && obj.get(SentinelConstants.JSON_FIELD_CACHE_BYPASS).getAsBoolean();
    }
}
