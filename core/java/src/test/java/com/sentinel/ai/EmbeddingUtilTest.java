package com.lumebridge.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbeddingUtilTest {

    @Test
    void unitVectorHasLengthOne() {
        float[] v = EmbeddingUtil.unitEmbeddingFromPayload("hello".getBytes(), 64);
        double sum = 0;
        for (float f : v) {
            sum += f * f;
        }
        assertEquals(1.0, sum, 1e-5);
    }

    @Test
    void deterministicForSamePayload() {
        byte[] p = "{\"a\":1}".getBytes();
        float[] a = EmbeddingUtil.unitEmbeddingFromPayload(p, 32);
        float[] b = EmbeddingUtil.unitEmbeddingFromPayload(p, 32);
        assertEquals(a.length, b.length);
        for (int i = 0; i < a.length; i++) {
            assertEquals(a[i], b[i], 1e-6f);
        }
    }

    @Test
    void cosineSelfIsOne() {
        float[] v = EmbeddingUtil.unitEmbeddingFromPayload("x".getBytes(), 16);
        double sim = EmbeddingUtil.cosineSimilarity(v, v);
        assertEquals(1.0, sim, 1e-5);
    }

    @Test
    void pgVectorLiteralIsBracketed() {
        float[] v = new float[] {0.1f, 0.2f};
        String lit = EmbeddingUtil.toPgVectorLiteral(v);
        assertTrue(lit.startsWith("[") && lit.endsWith("]"));
    }
}
