package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.MiddlewareFunc;
import com.lumebridge.pipeline.Plugin;
import com.lumebridge.pipeline.Stage;

import java.nio.charset.StandardCharsets;

/**
 * Estimates token usage for requests and responses.
 * Uses a simple 4-character-per-token heuristic for lightweight performance.
 */
public class TokenMeterPlugin implements Plugin {

    @Override public String name() { return SentinelConstants.PLUGIN_TOKEN_METER; }
    @Override public Stage stage() { return Stage.POST_PROCESS; }
    @Override public int order() { return 5; } // Last in POST_PROCESS

    @Override
    public MiddlewareFunc middleware() {
        return (ctx, next) -> {
            // 1. Calculate input tokens from raw payload
            byte[] inputRaw = ctx.getRawPayload();
            if (inputRaw != null) {
                int inputTokens = estimateTokens(new String(inputRaw, StandardCharsets.UTF_8));
                ctx.getMetadata().put(SentinelConstants.META_TOKENS_INPUT, String.valueOf(inputTokens));
                ctx.getMetrics().put("tokens_input", (long) inputTokens);
            }

            next.run();

            // 2. Calculate output tokens from result
            Object result = ctx.getResult();
            if (result != null) {
                int outputTokens = estimateTokens(result.toString());
                ctx.getMetadata().put(SentinelConstants.META_TOKENS_OUTPUT, String.valueOf(outputTokens));
                ctx.getMetrics().put("tokens_output", (long) outputTokens);
            }
        };
    }

    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        // Naive heuristic: 4 characters per token
        return (int) Math.ceil(text.length() / 4.0);
    }
}
