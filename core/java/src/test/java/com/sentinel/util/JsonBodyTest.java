package com.lumebridge.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonBodyTest {

    @Test
    void nullAndEmptyReturnNull() {
        assertNull(JsonBody.tryParse(null));
        assertNull(JsonBody.tryParse(new byte[0]));
    }

    @Test
    void invalidJsonReturnsNull() {
        assertNull(JsonBody.tryParse("{".getBytes()));
        assertNull(JsonBody.tryParse("[]".getBytes()));
    }

    @Test
    void objectParses() {
        var o = JsonBody.tryParse("{\"z\":1,\"a\":2}".getBytes());
        assertTrue(o.has("z"));
        assertTrue(o.has("a"));
    }

    @Test
    void primitiveJsonReturnsNull() {
        assertNull(JsonBody.tryParse("\"x\"".getBytes()));
        assertNull(JsonBody.tryParse("42".getBytes()));
    }

    @Test
    void safetyGuardrailsXmlEnvelopeStillParsesInnerJson() {
        String inner = "{\"query_intent\":\"TEMPORAL\",\"prompt\":\"when was X\"}";
        String wrapped = "<user_input>\n" + inner + "\n</user_input>";
        var o = JsonBody.tryParse(wrapped.getBytes());
        assertTrue(o.has("query_intent"));
        assertEquals("TEMPORAL", o.get("query_intent").getAsString());
    }
}
