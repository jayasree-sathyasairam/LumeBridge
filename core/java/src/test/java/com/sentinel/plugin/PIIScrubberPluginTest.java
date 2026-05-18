package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.RequestContext;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PIIScrubberPluginTest {

    @Test
    void testConsistentHashingAndNewPatterns() throws Exception {
        PIIScrubberPlugin pii = new PIIScrubberPlugin();
        
        // Input with two identical emails and one different email
        // Token must have 3 parts (header.payload.signature) for the regex to match reliably
        String raw = "User1: alice@test.com, User2: bob@test.com, User3: alice@test.com, IP: 192.168.1.1, Token: bearer eyJhbGci.eyJzdWIi.SflKxwR";
        RequestContext ctx = new RequestContext(raw.getBytes(StandardCharsets.UTF_8));
        
        pii.middleware().apply(ctx, () -> { });
        String out = new String(ctx.getRawPayload(), StandardCharsets.UTF_8);
        
        // 1. Verify Identical values get same hash
        // We'll extract the redaction tags
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\[EMAIL_([a-f0-9]{6})\\]").matcher(out);
        java.util.List<String> hashes = new java.util.ArrayList<>();
        while(m.find()) hashes.add(m.group(1));
        
        assertEquals(3, hashes.size(), "Should have found 3 emails");
        assertEquals(hashes.get(0), hashes.get(2), "Identical emails must have identical hashes");
        assertTrue(!hashes.get(0).equals(hashes.get(1)), "Different emails must have different hashes");
        
        // 2. Verify new patterns
        assertTrue(out.contains("[IP_"), "Should redact IP");
        assertTrue(out.contains("[TOKEN_"), "Should redact JWT");
    }

    @Test
    void doesNotCorruptJsonNonceDigitRun() throws Exception {
        PIIScrubberPlugin pii = new PIIScrubberPlugin();
        String raw = "{\"query_intent\":\"TEMPORAL\",\"prompt\":\"x\",\"nonce\":\"173712345678901234\"}";
        RequestContext ctx = new RequestContext(raw.getBytes(StandardCharsets.UTF_8));
        pii.middleware().apply(ctx, () -> { });
        assertEquals(raw, new String(ctx.getRawPayload(), StandardCharsets.UTF_8));
    }

    @Test
    void stillRedactsSpacedCardNumber() throws Exception {
        PIIScrubberPlugin pii = new PIIScrubberPlugin();
        String raw = "{\"note\":\"pay with 4111 1111 1111 1111\"}";
        RequestContext ctx = new RequestContext(raw.getBytes(StandardCharsets.UTF_8));
        pii.middleware().apply(ctx, () -> { });
        String out = new String(ctx.getRawPayload(), StandardCharsets.UTF_8);
        assertTrue(out.contains("[CARD_"), "Spaced PAN should still redact");
        assertTrue(!out.contains("4111"), "Digits should be masked");
    }
}
