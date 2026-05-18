package com.lumebridge.intent;

/**
 * V2 P1 coarse query categories for semantic-cache eligibility and scope buckets (TTL policy hooks).
 */
public enum QueryIntent {
    REAL_TIME,
    TEMPORAL,
    STATIC,
    CONVERSATION,
    COMPUTATION
}
