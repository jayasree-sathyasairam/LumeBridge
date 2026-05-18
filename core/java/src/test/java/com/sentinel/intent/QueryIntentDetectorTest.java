package com.sentinel.intent;

import com.lumebridge.intent.QueryIntent;
import com.lumebridge.intent.QueryIntentDetector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QueryIntentDetectorTest {

    @Test
    void detectsTemporalVersusRealtime() {
        assertEquals(QueryIntent.TEMPORAL, QueryIntentDetector.detect("What was the weather yesterday in NYC?"));
        assertEquals(QueryIntent.REAL_TIME, QueryIntentDetector.detect("What is the weather today in NYC?"));
    }

    @Test
    void computationOverridesStaticKeywords() {
        assertEquals(QueryIntent.COMPUTATION, QueryIntentDetector.detect("Solve this integral for me"));
    }

    @Test
    void defaultsToConversation() {
        assertEquals(QueryIntent.CONVERSATION, QueryIntentDetector.detect("Tell me a story"));
    }
}
