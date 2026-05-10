package com.lumebridge.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;

public final class JsonBody {

    private JsonBody() {}

    public static JsonObject tryParse(byte[] raw) {
        if (raw == null || raw.length == 0) {
            return null;
        }
        try {
            var el = JsonParser.parseString(new String(raw, StandardCharsets.UTF_8));
            return el.isJsonObject() ? el.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
