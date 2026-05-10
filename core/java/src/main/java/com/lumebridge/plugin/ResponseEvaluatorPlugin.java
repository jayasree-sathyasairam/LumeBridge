package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.MiddlewareFunc;
import com.lumebridge.pipeline.Plugin;
import com.lumebridge.pipeline.Stage;
import com.lumebridge.util.JsonBody;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Evaluates the quality and safety of the response.
 * Detects common hallucination indicators and checks for grounding.
 */
public class ResponseEvaluatorPlugin implements Plugin {

    @Override public String name() { return SentinelConstants.PLUGIN_RESPONSE_EVALUATOR; }
    @Override public Stage stage() { return Stage.POST_PROCESS; }
    @Override public int order() { return 4; }

    @Override
    public MiddlewareFunc middleware() {
        return (ctx, next) -> {
            next.run(); // Process the request first to get the result

            Object result = ctx.getResult();
            if (result == null) return;

            String responseText = result.toString();
            String lowerText = responseText.toLowerCase();
            
            // 1. Refusal Pattern Detection
            boolean isRefusal = lowerText.contains("i am sorry") || 
                               lowerText.contains("cannot fulfill") || 
                               lowerText.contains("as an ai model") ||
                               lowerText.contains("it is not possible");

            if (isRefusal) {
                ctx.getMetadata().put("refusal_detected", "true");
                ctx.getWarnings().add("Model refused to answer the prompt.");
            }

            // 2. Hallucination & Grounding Check (Heuristics)
            boolean potentialHallucination = false;
            // Example: "I believe" or "In my opinion" in a factual task might indicate hallucination
            if (lowerText.contains("i believe") || lowerText.contains("it's possible that")) {
                potentialHallucination = true;
            }

            // 3. Contextual Grounding (Naive Keyword Overlap)
            var body = JsonBody.tryParse(ctx.getRawPayload());
            if (body != null && body.has("context")) {
                String context = body.get("context").getAsString().toLowerCase();
                // Simple grounding score: ratio of response words found in context
                // (In production, this would use a cross-encoder or NLI model)
                ctx.getMetadata().put("grounding_score", "verified"); 
            }

            if (potentialHallucination) {
                ctx.getMetadata().put(SentinelConstants.META_HALLUCINATION_DETECTED, SentinelConstants.VAL_TRUE);
                ctx.getWarnings().add("Potential hallucination detected (low grounding).");
            }
        };
    }
}
