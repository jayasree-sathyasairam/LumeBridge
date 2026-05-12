package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.MiddlewareFunc;
import com.lumebridge.pipeline.Plugin;
import com.lumebridge.pipeline.RequestContext;
import com.lumebridge.pipeline.Stage;
import com.lumebridge.util.JsonBody;

import java.util.List;
import java.util.Map;

/**
 * Evaluates the quality and safety of the response using pluggable evaluators.
 * Reduces cyclomatic complexity by delegating to strategy objects.
 */
public class ResponseEvaluatorPlugin implements Plugin {

    private List<ResponseEvaluator> evaluators;

    public ResponseEvaluatorPlugin() {
        this.evaluators = List.of(
            new RefusalDetector(),
            new HallucinationDetector(),
            new GroundingChecker()
        );
    }

    @Override public String name() { return SentinelConstants.PLUGIN_RESPONSE_EVALUATOR; }
    @Override public Stage stage() { return Stage.POST_PROCESS; }
    @Override public int order() { return 4; }

    @Override
    public MiddlewareFunc middleware() {
        return (ctx, next) -> {
            next.run();

            Object result = ctx.getResult();
            if (isEmpty(result)) {
                return;
            }

            String responseText = result.toString();
            for (ResponseEvaluator evaluator : evaluators) {
                evaluator.evaluate(ctx, responseText);
            }
        };
    }

    private boolean isEmpty(Object value) {
        return value == null;
    }

    interface ResponseEvaluator {
        void evaluate(RequestContext ctx, String responseText);
    }

    static class RefusalDetector implements ResponseEvaluator {
        private static final List<String> REFUSAL_PATTERNS = List.of(
            "i am sorry",
            "cannot fulfill",
            "as an ai model",
            "it is not possible"
        );

        @Override
        public void evaluate(RequestContext ctx, String responseText) {
            String lower = responseText.toLowerCase();
            for (String pattern : REFUSAL_PATTERNS) {
                if (lower.contains(pattern)) {
                    ctx.getMetadata().put("refusal_detected", "true");
                    ctx.getWarnings().add("Model refused to answer the prompt.");
                    return;
                }
            }
        }
    }

    static class HallucinationDetector implements ResponseEvaluator {
        private static final List<String> UNCERTAINTY_MARKERS = List.of(
            "i believe",
            "it's possible that",
            "i think",
            "in my opinion"
        );

        @Override
        public void evaluate(RequestContext ctx, String responseText) {
            String lower = responseText.toLowerCase();
            for (String marker : UNCERTAINTY_MARKERS) {
                if (lower.contains(marker)) {
                    ctx.getMetadata().put(SentinelConstants.META_HALLUCINATION_DETECTED, SentinelConstants.VAL_TRUE);
                    ctx.getWarnings().add("Potential hallucination detected (uncertainty markers present).");
                    return;
                }
            }
        }
    }

    static class GroundingChecker implements ResponseEvaluator {
        @Override
        public void evaluate(RequestContext ctx, String responseText) {
            var body = JsonBody.tryParse(ctx.getRawPayload());
            if (body == null || !body.has("context")) {
                return;
            }

            String context = body.get("context").getAsString().toLowerCase();
            double groundingScore = calculateGroundingScore(responseText.toLowerCase(), context);

            if (groundingScore >= 0.7) {
                ctx.getMetadata().put("grounding_score", "verified");
            } else {
                ctx.getMetadata().put("grounding_score", String.format("%.2f", groundingScore));
            }

            if (groundingScore < 0.5) {
                ctx.getWarnings().add("Low grounding score. Response may not be grounded in provided context.");
            }
        }

        private double calculateGroundingScore(String response, String context) {
            String[] responseWords = response.split("\\s+");
            int matches = 0;
            for (String word : responseWords) {
                if (context.contains(word)) {
                    matches++;
                }
            }
            return (double) matches / responseWords.length;
        }
    }
}
