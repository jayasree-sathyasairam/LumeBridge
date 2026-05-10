package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.MiddlewareFunc;
import com.lumebridge.pipeline.Plugin;
import com.lumebridge.pipeline.Stage;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Detects and blocks jailbreak attempts and restricted topics in the input prompt.
 * Lightweight regex-based implementation.
 */
public class SafetyGuardrailsPlugin implements Plugin {

    // Jailbreak and Persona patterns
    private static final List<Pattern> JAILBREAK_PATTERNS = List.of(
            Pattern.compile("(?i)ignore (?:all )?previous instructions"),
            Pattern.compile("(?i)disregard (?:all )?previous instructions"),
            Pattern.compile("(?i)you are now (?:in )?DAN mode"),
            Pattern.compile("(?i)developer mode (?:enabled|on)"),
            Pattern.compile("(?i)simulate (?:a )?jailbroken"),
            Pattern.compile("(?i)reveal your (?:hidden )?instructions"),
            Pattern.compile("(?i)system prompt (?:is|was):"),
            Pattern.compile("(?i)<\\/user_input>") // Escape attempt
    );

    // Restricted keywords (Safety + Topic Avoidance)
    private static final List<String> RESTRICTED_KEYWORDS = List.of(
            "bomb", "weapon", "explosive", "hack", "bypass", // Safety (RESTORED)
            "diagnose", "treatment", "medical advice",      // Medical Avoidance
            "investment", "buy stock", "financial advice",  // Financial Avoidance
            "competitor_brand_a", "competitor_brand_b"      // Brand Protection (Example)
    );

    @Override public String name() { return SentinelConstants.PLUGIN_SAFETY_GUARDRAILS; }
    @Override public Stage stage() { return Stage.PRE_PROCESS; }
    @Override public int order() { return 4; } 

    @Override
    public MiddlewareFunc middleware() {
        return (ctx, next) -> {
            byte[] raw = ctx.getRawPayload();
            if (raw == null || raw.length == 0) {
                next.run();
                return;
            }

            String fullText = new String(raw, StandardCharsets.UTF_8);
            
            // Extract just the 'prompt' field for safety scanning
            String safetyTarget = fullText; 
            var body = com.lumebridge.util.JsonBody.tryParse(raw);
            if (body != null && body.has(SentinelConstants.JSON_FIELD_PROMPT)) {
                safetyTarget = body.get(SentinelConstants.JSON_FIELD_PROMPT).getAsString();
            }

            // 1. Delimiter & Injection Check (on the prompt only)
            for (Pattern p : JAILBREAK_PATTERNS) {
                if (p.matcher(safetyTarget).find()) {
                    fail(ctx, "Safety violation: Injection/Jailbreak attempt detected.");
                    return;
                }
            }

            // 2. Topic Avoidance Check (on the prompt only)
            String lowerTarget = safetyTarget.toLowerCase();
            for (String kw : RESTRICTED_KEYWORDS) {
                if (lowerTarget.contains(kw)) {
                    fail(ctx, "Safety violation: Restricted topic detected.");
                    return;
                }
            }

            // 3. Delimiter Enforcement (XML Wrapping)
            // Wraps the raw payload to prevent model confusion
            String wrapped = "<user_input>\n" + fullText + "\n</user_input>";
            ctx.setRawPayload(wrapped.getBytes(StandardCharsets.UTF_8));

            next.run();
        };
    }

    private void fail(com.lumebridge.pipeline.RequestContext ctx, String reason) {
        ctx.setHttpStatus(SentinelConstants.HTTP_STATUS_BAD_REQUEST);
        ctx.setStatus(SentinelConstants.STATUS_ERROR);
        ctx.getMetadata().put(SentinelConstants.META_SAFETY_VIOLATION, SentinelConstants.VAL_TRUE);
        ctx.setError(new RuntimeException(reason));
    }
}
