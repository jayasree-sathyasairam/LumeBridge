package com.lumebridge.ai;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Deterministic pseudo-embeddings from payload bytes (unit vector) for semantic-cache demos and tests
 * without an external embedding API.
 */
public final class EmbeddingUtil {

    private EmbeddingUtil() {}

    public static float[] unitEmbeddingFromPayload(byte[] payload, int dimensions) {
        if (dimensions < 2) {
            throw new IllegalArgumentException("dimensions must be >= 2");
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(payload == null ? new byte[0] : payload);
            float[] v = new float[dimensions];
            for (int i = 0; i < dimensions; i++) {
                int b1 = digest[i % digest.length] & 0xFF;
                int b2 = digest[(i + 17) % digest.length] & 0xFF;
                v[i] = ((b1 / 255f) * 2f - 1f) * 0.5f + ((b2 / 255f) * 2f - 1f) * 0.5f;
            }
            normalizeInPlace(v);
            return v;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static void normalizeInPlace(float[] v) {
        double sumSq = 0;
        for (float f : v) {
            sumSq += (double) f * f;
        }
        double norm = Math.sqrt(sumSq);
        if (norm < 1e-9) {
            Arrays.fill(v, 0f);
            v[0] = 1f;
            return;
        }
        for (int i = 0; i < v.length; i++) {
            v[i] = (float) (v[i] / norm);
        }
    }

    /** pgvector literal for JDBC, e.g. {@code '[0.1,0.2,...]'}. */
    public static String toPgVectorLiteral(float[] v) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(Float.toString(v[i]));
        }
        sb.append(']');
        return sb.toString();
    }

    public static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("dimension mismatch");
        }
        double dot = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return dot;
    }

    public static String payloadTextForEmbedding(byte[] raw) {
        if (raw == null || raw.length == 0) {
            return "";
        }
        return new String(raw, StandardCharsets.UTF_8);
    }
}
