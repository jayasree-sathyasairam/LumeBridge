package com.lumebridge.plugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.MiddlewareFunc;
import com.lumebridge.pipeline.Plugin;
import com.lumebridge.pipeline.Stage;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/**
 * Canonicalizes JSON object keys (sorted recursively) for stable hashing and deduplication.
 */
public class PayloadNormalizerPlugin implements Plugin {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    @Override public String name() { return SentinelConstants.PLUGIN_PAYLOAD_NORMALIZER; }
    @Override public Stage stage() { return Stage.PRE_PROCESS; }
    @Override public int order() { return 2; }

    @Override
    public MiddlewareFunc middleware() {
        return (ctx, next) -> {
            byte[] raw = ctx.getRawPayload();
            try {
                var el = com.google.gson.JsonParser.parseString(new String(raw, StandardCharsets.UTF_8));
                if (el.isJsonObject()) {
                    JsonObject sorted = sortKeysDeep(el.getAsJsonObject());
                    ctx.setRawPayload(GSON.toJson(sorted).getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                ctx.getWarnings().add(SentinelConstants.WARN_PAYLOAD_NORMALIZER_PREFIX + e.getMessage());
            }
            next.run();
        };
    }

    private static JsonObject sortKeysDeep(JsonObject in) {
        TreeMap<String, JsonElement> sorted = new TreeMap<>();
        for (Map.Entry<String, JsonElement> e : in.entrySet()) {
            sorted.put(e.getKey(), normalizeElement(e.getValue()));
        }
        JsonObject out = new JsonObject();
        for (Map.Entry<String, JsonElement> e : sorted.entrySet()) {
            out.add(e.getKey(), e.getValue());
        }
        return out;
    }

    private static JsonElement normalizeElement(JsonElement v) {
        if (v.isJsonObject()) {
            return sortKeysDeep(v.getAsJsonObject());
        }
        if (v.isJsonArray()) {
            JsonArray arr = v.getAsJsonArray();
            JsonArray out = new JsonArray();
            for (JsonElement inner : arr) {
                out.add(normalizeElement(inner));
            }
            return out;
        }
        return v;
    }
}
