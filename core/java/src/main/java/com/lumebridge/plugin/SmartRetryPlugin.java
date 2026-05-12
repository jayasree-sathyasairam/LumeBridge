package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.MiddlewareFunc;
import com.lumebridge.pipeline.Plugin;
import com.lumebridge.pipeline.RequestContext;
import com.lumebridge.pipeline.Stage;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Retries the remainder of the pipeline when it throws, using exponential backoff with jitter.
 * Plugins that only set {@code RequestContext} status without throwing are not retried.
 * Optional {@link SentinelConstants#CFG_KEY_RETRIABLE_MESSAGE_SUBSTRINGS}: exception message must contain one of these (case-insensitive), or any message if empty.
 */
public class SmartRetryPlugin implements Plugin {

    private int maxRetries = SentinelConstants.DEFAULT_SMART_RETRY_MAX_RETRIES;
    private long baseDelayMs = SentinelConstants.DEFAULT_SMART_RETRY_BASE_DELAY_MS;
    private long maxDelayMs = SentinelConstants.DEFAULT_SMART_RETRY_MAX_DELAY_MS;
    private int jitterPercent = SentinelConstants.DEFAULT_SMART_RETRY_JITTER_PERCENT;
    /** Substrings; empty means all thrown exceptions are retriable up to maxRetries. */
    private Set<String> retriableSubstrings = Set.of();

    @Override public String name() { return SentinelConstants.PLUGIN_SMART_RETRY; }
    @Override public Stage stage() { return Stage.EXECUTE; }
    @Override public int order() { return 2; }

    @Override
    public void init(Map<String, String> config) {
        this.maxRetries = Integer.parseInt(config.getOrDefault(
                SentinelConstants.CFG_KEY_MAX_RETRIES,
                Integer.toString(SentinelConstants.DEFAULT_SMART_RETRY_MAX_RETRIES)));
        this.baseDelayMs = Long.parseLong(config.getOrDefault(
                SentinelConstants.CFG_KEY_BASE_DELAY_MS,
                Integer.toString(SentinelConstants.DEFAULT_SMART_RETRY_BASE_DELAY_MS)));
        this.maxDelayMs = Long.parseLong(config.getOrDefault(
                SentinelConstants.CFG_KEY_MAX_DELAY_MS,
                Integer.toString(SentinelConstants.DEFAULT_SMART_RETRY_MAX_DELAY_MS)));
        this.jitterPercent = Integer.parseInt(config.getOrDefault(
                SentinelConstants.CFG_KEY_JITTER_PERCENT,
                Integer.toString(SentinelConstants.DEFAULT_SMART_RETRY_JITTER_PERCENT)));
        String raw = config.get(SentinelConstants.CFG_KEY_RETRIABLE_MESSAGE_SUBSTRINGS);
        if (raw != null && !raw.isBlank()) {
            this.retriableSubstrings = Arrays.stream(raw.split(SentinelConstants.SPLIT_PIPE_COMMA))
                    .map(s -> s.trim().toLowerCase(Locale.ROOT))
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toSet());
        }
    }

    @Override
    public MiddlewareFunc middleware() {
        return (ctx, next) -> {
            int attempt = 0;
            while (true) {
                try {
                    next.run();
                    return;
                } catch (RuntimeException e) {
                    Throwable cause = e.getCause() != null ? e.getCause() : e;
                    if (attempt >= maxRetries || !isRetriable(cause)) {
                        throw e;
                    }
                    sleepWithBackoff(attempt, ctx);
                    attempt++;
                }
            }
        };
    }

    private boolean isRetriable(Throwable cause) {
        if (cause instanceof IOException) {
            return true;
        }
        if (retriableSubstrings.isEmpty()) {
            return true;
        }
        String msg = cause.getMessage();
        if (msg == null) {
            return false;
        }
        String lower = msg.toLowerCase(Locale.ROOT);
        return containsRetriableKeyword(lower);
    }

    private boolean containsRetriableKeyword(String lowerMsg) {
        return retriableSubstrings.stream().anyMatch(lowerMsg::contains);
    }

    private void sleepWithBackoff(int attemptZeroBased, RequestContext ctx) {
        long cap = Math.min(maxDelayMs, baseDelayMs * (1L << attemptZeroBased));
        long retryAfter = parseRetryAfterMs(ctx);
        long base = Math.max(cap, retryAfter > 0 ? retryAfter : 0);
        long jitter = (long) (base * jitterPercent / 100.0 * Math.random());
        long delay = Math.min(maxDelayMs, base + jitter);
        try {
            Thread.sleep(Math.max(1, delay));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(ie);
        }
    }

    private static long parseRetryAfterMs(RequestContext ctx) {
        String v = ctx.getMetadata().get(SentinelConstants.META_RETRY_AFTER_MS);
        if (v == null) {
            return 0;
        }
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
