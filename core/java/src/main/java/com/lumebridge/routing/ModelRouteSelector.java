package com.lumebridge.routing;

import com.lumebridge.SentinelConstants;
import com.lumebridge.cache.SemanticCacheContextBuilder;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * V2 P1 cost-aware routing: keyword complexity plus a crude token estimate from UTF-8 payload size.
 */
public final class ModelRouteSelector {

    public enum Complexity {
        LOW,
        MEDIUM,
        HIGH
    }

    private static final Pattern HIGH = Pattern.compile(
            "(?i)\\b(design|architecture|analyze\\s+deeply|critique|evaluate thoroughly|rigorous proof|formal verification)\\b");
    private static final Pattern MEDIUM = Pattern.compile(
            "(?i)\\b(summarize|explain|compare|outline|describe|list the)\\b");

    private ModelRouteSelector() {}

    public static Complexity detectComplexity(String promptText) {
        if (promptText == null || promptText.isBlank()) {
            return Complexity.LOW;
        }
        String t = promptText.toLowerCase(Locale.ROOT);
        if (HIGH.matcher(t).find()) {
            return Complexity.HIGH;
        }
        if (MEDIUM.matcher(t).find()) {
            return Complexity.MEDIUM;
        }
        return Complexity.LOW;
    }

    /** Rough prompt token estimate (~4 chars/token for Latin text). */
    public static int approximatePromptTokens(byte[] rawPayload) {
        String prompt = SemanticCacheContextBuilder.promptTextFromPayload(rawPayload);
        int fromPrompt = Math.max(1, prompt.length()) / 4;
        int fromRaw = rawPayload == null ? 0 : Math.max(1, rawPayload.length) / 4;
        return Math.max(fromPrompt, fromRaw);
    }

    public static String selectPrimaryRoute(Complexity complexity, int approxTokens, boolean payloadVeryLarge) {
        if (payloadVeryLarge) {
            return SentinelConstants.ROUTE_GPT4_TURBO;
        }
        if (complexity == Complexity.HIGH || approxTokens >= 2000) {
            return SentinelConstants.ROUTE_GPT4_TURBO;
        }
        if (complexity == Complexity.MEDIUM || approxTokens >= 500) {
            return SentinelConstants.ROUTE_GPT4O;
        }
        return SentinelConstants.ROUTE_GPT4O_MINI;
    }

    public static String selectFallbackRoute(String primaryRoute) {
        if (SentinelConstants.ROUTE_GPT4_TURBO.equals(primaryRoute)) {
            return SentinelConstants.ROUTE_GPT4O;
        }
        if (SentinelConstants.ROUTE_GPT4O.equals(primaryRoute)) {
            return SentinelConstants.ROUTE_GPT4O_MINI;
        }
        if (SentinelConstants.ROUTE_GPT4O_MINI.equals(primaryRoute)) {
            return SentinelConstants.ROUTE_CLAUDE_HAIKU;
        }
        return SentinelConstants.ROUTE_CLAUDE_HAIKU;
    }
}
