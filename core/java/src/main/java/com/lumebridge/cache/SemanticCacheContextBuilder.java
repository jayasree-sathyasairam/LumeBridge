package com.lumebridge.cache;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lumebridge.SentinelConstants;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * V2 P0: binds semantic-cache similarity to <strong>tenant</strong> (API key fingerprint) and a coarse
 * <strong>freshness / temporal</strong> bucket derived from prompt text — avoids cross-leaking “today” vs
 * “yesterday” answers when embeddings stay dangerously similar.
 */
public final class SemanticCacheContextBuilder {

    private static final Pattern ISO_DATE = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private SemanticCacheContextBuilder() {}

    /** Stable fingerprint for {@code X-API-Key}; anonymous clients share one bucket. */
    public static String tenantFingerprint(String apiKeyHeader) {
        if (apiKeyHeader == null || apiKeyHeader.isBlank()) {
            return "anonymous";
        }
        return sha256Hex(apiKeyHeader.strip());
    }

    /**
     * Coarse temporal / freshness bucket from payload body (JSON {@code prompt} or flattened primitives).
     * Intentionally heuristic until P1 intent classification feeds explicit TTL buckets.
     */
    public static String freshnessBucket(byte[] rawPayload) {
        String text = promptTextFromPayload(rawPayload).toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return "TIME_UNSPECIFIED";
        }
        if (text.contains("today") || text.contains("right now") || text.contains("current weather")
                || text.contains("currently")) {
            return "REALTIME_OR_TODAY";
        }
        if (text.contains("yesterday") || text.contains("last night") || text.contains("previous day")) {
            return "RELATIVE_YESTERDAY";
        }
        if (ISO_DATE.matcher(text).find()) {
            return "DATE_LITERAL";
        }
        if (text.contains("historical") || text.contains("history")) {
            return "HISTORY_SEMANTIC";
        }
        return "TIME_UNSPECIFIED";
    }

    public static String cacheScope(String tenantFingerprint, String freshnessBucket) {
        return tenantFingerprint + ":" + freshnessBucket;
    }

    /** Bytes fed into deterministic pseudo-embedding (must stay stable for lookup vs stored rows). */
    public static byte[] embeddingMaterial(byte[] rawPayload, String tenantFingerprint, String freshnessBucket) {
        String prompt = promptTextFromPayload(rawPayload);
        String merged = freshnessBucket + "\u001f" + tenantFingerprint + "\u001f" + prompt;
        return merged.getBytes(StandardCharsets.UTF_8);
    }

    static String promptTextFromPayload(byte[] raw) {
        if (raw == null || raw.length == 0) {
            return "";
        }
        try {
            String s = new String(raw, StandardCharsets.UTF_8).trim();
            JsonElement el = JsonParser.parseString(s);
            if (!el.isJsonObject()) {
                return s;
            }
            JsonObject o = el.getAsJsonObject();
            if (o.has(SentinelConstants.JSON_FIELD_PROMPT) && !o.get(SentinelConstants.JSON_FIELD_PROMPT).isJsonNull()) {
                JsonElement p = o.get(SentinelConstants.JSON_FIELD_PROMPT);
                if (p.isJsonPrimitive()) {
                    return p.getAsString();
                }
                return p.toString();
            }
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                if (SentinelConstants.JSON_FIELD_NONCE.equalsIgnoreCase(e.getKey())
                        || SentinelConstants.JSON_FIELD_CACHE_BYPASS.equalsIgnoreCase(e.getKey())) {
                    continue;
                }
                JsonElement v = e.getValue();
                if (v != null && v.isJsonPrimitive()) {
                    sb.append(e.getKey()).append('=').append(v.getAsString()).append(';');
                }
            }
            return sb.isEmpty() ? s : sb.toString();
        } catch (Exception e) {
            return new String(raw, StandardCharsets.UTF_8);
        }
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
