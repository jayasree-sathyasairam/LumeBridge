package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.MiddlewareFunc;
import com.lumebridge.pipeline.Plugin;
import com.lumebridge.pipeline.RequestContext;
import com.lumebridge.pipeline.Stage;
import com.lumebridge.util.JsonBody;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Detects and blocks jailbreak attempts and restricted topics in the input prompt.
 * Delegates to pluggable SafetyChecker strategies to reduce cyclomatic complexity.
 */
public class SafetyGuardrailsPlugin implements Plugin {

    private List<SafetyChecker> checkers;

    public SafetyGuardrailsPlugin() {
        this.checkers = List.of(
            new JailbreakDetector(),
            new RestrictedTopicDetector()
        );
    }

    @Override public String name() { return SentinelConstants.PLUGIN_SAFETY_GUARDRAILS; }
    @Override public Stage stage() { return Stage.PRE_PROCESS; }
    @Override public int order() { return 4; }

    @Override
    public MiddlewareFunc middleware() {
        return (ctx, next) -> {
            byte[] raw = ctx.getRawPayload();
            if (isEmpty(raw)) {
                next.run();
                return;
            }

            String fullText = new String(raw, StandardCharsets.UTF_8);
            String safetyTarget = extractPromptField(raw, fullText);

            for (SafetyChecker checker : checkers) {
                String violation = checker.check(safetyTarget);
                if (hasViolation(violation)) {
                    failRequest(ctx, violation);
                    return;
                }
            }

            String wrapped = "<user_input>\n" + fullText + "\n</user_input>";
            ctx.setRawPayload(wrapped.getBytes(StandardCharsets.UTF_8));
            next.run();
        };
    }

    private boolean isEmpty(byte[] raw) {
        return raw == null || raw.length == 0;
    }

    private boolean hasViolation(String violation) {
        return violation != null && !violation.isEmpty();
    }

    private String extractPromptField(byte[] raw, String fullText) {
        var body = JsonBody.tryParse(raw);
        if (isValidJsonWithPrompt(body)) {
            return body.get(SentinelConstants.JSON_FIELD_PROMPT).getAsString();
        }
        return fullText;
    }

    private boolean isValidJsonWithPrompt(com.google.gson.JsonElement body) {
        return body != null && body.isJsonObject()
               && body.getAsJsonObject().has(SentinelConstants.JSON_FIELD_PROMPT);
    }

    private void failRequest(RequestContext ctx, String reason) {
        ctx.setHttpStatus(SentinelConstants.HTTP_STATUS_BAD_REQUEST);
        ctx.setStatus(SentinelConstants.STATUS_ERROR);
        ctx.getMetadata().put(SentinelConstants.META_SAFETY_VIOLATION, SentinelConstants.VAL_TRUE);
        ctx.setError(new RuntimeException(reason));
    }

    interface SafetyChecker {
        String check(String text);
    }

    static class JailbreakDetector implements SafetyChecker {
        private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("(?i)ignore (?:all )?previous instructions"),
            Pattern.compile("(?i)disregard (?:all )?previous instructions"),
            Pattern.compile("(?i)you are now (?:in )?DAN mode"),
            Pattern.compile("(?i)developer mode (?:enabled|on)"),
            Pattern.compile("(?i)simulate (?:a )?jailbroken"),
            Pattern.compile("(?i)reveal your (?:hidden )?instructions"),
            Pattern.compile("(?i)system prompt (?:is|was):"),
            Pattern.compile("(?i)<\\/user_input>")
        );

        @Override
        public String check(String text) {
            for (Pattern p : PATTERNS) {
                if (p.matcher(text).find()) {
                    return "Safety violation: Injection/Jailbreak attempt detected.";
                }
            }
            return null;
        }
    }

    static class RestrictedTopicDetector implements SafetyChecker {
        private static final List<String> KEYWORDS = List.of(
            "bomb", "weapon", "explosive", "hack", "bypass",
            "diagnose", "treatment", "medical advice",
            "investment", "buy stock", "financial advice",
            "competitor_brand_a", "competitor_brand_b"
        );

        @Override
        public String check(String text) {
            String lower = text.toLowerCase();
            for (String kw : KEYWORDS) {
                if (lower.contains(kw)) {
                    return "Safety violation: Restricted topic detected.";
                }
            }
            return null;
        }
    }
}
