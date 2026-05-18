package com.lumebridge.payload;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.TreeMap;

/** Canonical {@code application/x-www-form-urlencoded}: sorted keys, UTF-8 percent-encoding. */
public final class FormUrlEncodedNormalizer {

    private FormUrlEncodedNormalizer() {}

    public static byte[] normalizeUtf8(byte[] raw) {
        String s = new String(raw, StandardCharsets.UTF_8);
        TreeMap<String, String> pairs = new TreeMap<>();
        if (!s.isEmpty()) {
            for (String part : s.split("&")) {
                if (part.isEmpty()) {
                    continue;
                }
                int eq = part.indexOf('=');
                String k = eq < 0 ? part : part.substring(0, eq);
                String v = eq < 0 ? "" : part.substring(eq + 1);
                pairs.put(
                        URLDecoder.decode(k, StandardCharsets.UTF_8),
                        URLDecoder.decode(v, StandardCharsets.UTF_8));
            }
        }
        StringBuilder out = new StringBuilder();
        for (var e : pairs.entrySet()) {
            if (!out.isEmpty()) {
                out.append('&');
            }
            out.append(URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8));
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }
}
