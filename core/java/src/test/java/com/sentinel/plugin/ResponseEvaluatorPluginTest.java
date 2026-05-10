package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.RequestContext;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ResponseEvaluatorPluginTest {

    @Test
    void detectsRefusal() throws Exception {
        ResponseEvaluatorPlugin evaluator = new ResponseEvaluatorPlugin();
        RequestContext ctx = new RequestContext("{}".getBytes(StandardCharsets.UTF_8));
        
        // Mock the next stage setting a refusal result
        evaluator.middleware().apply(ctx, () -> {
            ctx.setResult("I am sorry, but I cannot fulfill this request as an AI model.");
        });

        assertEquals("true", ctx.getMetadata().get("refusal_detected"));
        assertTrue(ctx.getWarnings().stream().anyMatch(w -> w.contains("refused")));
    }

    @Test
    void detectsPotentialHallucination() throws Exception {
        ResponseEvaluatorPlugin evaluator = new ResponseEvaluatorPlugin();
        RequestContext ctx = new RequestContext("{}".getBytes(StandardCharsets.UTF_8));
        
        evaluator.middleware().apply(ctx, () -> {
            ctx.setResult("I believe the answer might be 42, it's possible that I'm wrong.");
        });

        assertEquals(SentinelConstants.VAL_TRUE, ctx.getMetadata().get(SentinelConstants.META_HALLUCINATION_DETECTED));
        assertTrue(ctx.getWarnings().stream().anyMatch(w -> w.contains("hallucination")));
    }

    @Test
    void verifiesGroundingWhenContextPresent() throws Exception {
        ResponseEvaluatorPlugin evaluator = new ResponseEvaluatorPlugin();
        String raw = "{\"context\": \"The capital of France is Paris.\"}";
        RequestContext ctx = new RequestContext(raw.getBytes(StandardCharsets.UTF_8));
        
        evaluator.middleware().apply(ctx, () -> {
            ctx.setResult("Paris is the capital.");
        });

        assertEquals("verified", ctx.getMetadata().get("grounding_score"));
    }
}
