package com.lumebridge.payload;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.Map;
import java.util.TreeMap;

/**
 * Converts loosely-typed trees (YAML/SnakeYAML, MessagePack, etc.) into Gson {@link JsonElement}
 * with lexicographically sorted object keys at every level (stable hashing).
 */
public final class StructuredPayloadConverter {

    private StructuredPayloadConverter() {}

    public static JsonElement toJsonElement(Object o) {
        if (o == null) {
            return JsonNull.INSTANCE;
        }
        if (o instanceof Map<?, ?> m) {
            JsonObject jo = new JsonObject();
            TreeMap<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> e : m.entrySet()) {
                sorted.put(String.valueOf(e.getKey()), e.getValue());
            }
            for (Map.Entry<String, Object> e : sorted.entrySet()) {
                jo.add(e.getKey(), toJsonElement(e.getValue()));
            }
            return jo;
        }
        if (o instanceof Iterable<?> it) {
            JsonArray arr = new JsonArray();
            for (Object x : it) {
                arr.add(toJsonElement(x));
            }
            return arr;
        }
        if (o.getClass().isArray()) {
            JsonArray arr = new JsonArray();
            int len = java.lang.reflect.Array.getLength(o);
            for (int i = 0; i < len; i++) {
                arr.add(toJsonElement(java.lang.reflect.Array.get(o, i)));
            }
            return arr;
        }
        if (o instanceof Number n) {
            return new JsonPrimitive(n);
        }
        if (o instanceof Boolean b) {
            return new JsonPrimitive(b);
        }
        return new JsonPrimitive(String.valueOf(o));
    }
}
