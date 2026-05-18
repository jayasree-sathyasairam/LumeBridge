package com.lumebridge.util;

import java.nio.charset.StandardCharsets;

/**
 * Strips the safety-guardrails envelope {@link com.lumebridge.plugin.SafetyGuardrailsPlugin}
 * adds around JSON ({@code <user_input>\n...\n</user_input>}) so downstream plugins can still parse JSON fields.
 */
public final class PayloadUnwrap {

    private static final String USER_INPUT_OPEN = "<user_input>\n";
    private static final String USER_INPUT_CLOSE = "\n</user_input>";

    private PayloadUnwrap() {}

    /** Returns inner bytes when wrapped; otherwise {@code raw} unchanged. */
    public static byte[] unwrapUserInputEnvelope(byte[] raw) {
        if (raw == null || raw.length == 0) {
            return raw;
        }
        String t = new String(raw, StandardCharsets.UTF_8).trim();
        if (t.startsWith(USER_INPUT_OPEN) && t.endsWith(USER_INPUT_CLOSE)) {
            String inner = t.substring(USER_INPUT_OPEN.length(), t.length() - USER_INPUT_CLOSE.length());
            return inner.getBytes(StandardCharsets.UTF_8);
        }
        return raw;
    }
}
