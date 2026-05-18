package com.lumebridge.intent;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Lightweight pattern-based classifier (roadmap Week 2). Prefer explicit signals first; default {@link QueryIntent#CONVERSATION}.
 */
public final class QueryIntentDetector {

    private static final Pattern ISO_DATE = Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b");
    private static final Pattern YEAR = Pattern.compile("\\b(19|20)\\d{2}\\b");

    private static final Pattern CONVERSATION = Pattern.compile(
            "(?i)\\b(earlier discussion|based on our earlier|as we discussed|previous (message|turn)|from earlier)\\b");
    private static final Pattern COMPUTATION = Pattern.compile(
            "(?i)\\b(solve|calculate|compute|integral|derivative|equation|prove\\s+that|matrix multiplication)\\b");
    private static final Pattern TEMPORAL = Pattern.compile(
            "(?i)\\b(yesterday|last\\s+week|last\\s+month|last\\s+year|historical|previous\\s+day)\\b");
    private static final Pattern REAL_TIME = Pattern.compile(
            "(?i)\\b(now|right\\s+now|currently|latest|today)\\b");
    private static final Pattern STATIC = Pattern.compile(
            "(?i)\\b(capital of|definition of|largest city|atomic number|who invented)\\b");

    private QueryIntentDetector() {}

    public static QueryIntent detect(String promptText) {
        if (promptText == null || promptText.isBlank()) {
            return QueryIntent.CONVERSATION;
        }
        String t = promptText.toLowerCase(Locale.ROOT);
        if (CONVERSATION.matcher(t).find()) {
            return QueryIntent.CONVERSATION;
        }
        if (COMPUTATION.matcher(t).find()) {
            return QueryIntent.COMPUTATION;
        }
        if (TEMPORAL.matcher(t).find() || ISO_DATE.matcher(t).find() || YEAR.matcher(t).find()) {
            return QueryIntent.TEMPORAL;
        }
        if (REAL_TIME.matcher(t).find()) {
            return QueryIntent.REAL_TIME;
        }
        if (STATIC.matcher(t).find()) {
            return QueryIntent.STATIC;
        }
        return QueryIntent.CONVERSATION;
    }
}
