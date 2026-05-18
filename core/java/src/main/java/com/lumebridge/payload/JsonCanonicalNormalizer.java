package com.lumebridge.payload;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/** Stable UTF-8 JSON for objects (recursive sorted keys). Arrays / primitives left unchanged. */
public final class JsonCanonicalNormalizer {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private JsonCanonicalNormalizer() {}

    /** Canonicalizes JSON objects only; otherwise returns {@code raw} unchanged. */
    public static byte[] normalizeUtf8(byte[] raw) {
        String s = new String(raw, StandardCharsets.UTF_8);
        JsonElement el = JsonParser.parseString(s);
        if (!el.isJsonObject()) {
            return raw;
        }
        JsonObject sorted = sortKeysDeep(el.getAsJsonObject());
        return GSON.toJson(sorted).getBytes(StandardCharsets.UTF_8);
    }

    static JsonObject sortKeysDeep(JsonObject in) {
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
