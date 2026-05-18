package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.MiddlewareFunc;
import com.lumebridge.pipeline.Plugin;
import com.lumebridge.pipeline.Stage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks PII patterns in the raw JSON payload bytes.
 * Uses consistent hashing (Format-Preserving-ish) to allow LLMs to differentiate entities.
 */
public class PIIScrubberPlugin implements Plugin {

    private static final Pattern EMAIL = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b");
    private static final Pattern PHONE = Pattern.compile("\\b(?:\\+?\\d{1,3}[- ]?)?\\(?\\d{3}\\)?[- ]?\\d{3}[- ]?\\d{4}\\b");
    private static final Pattern SSN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    /**
     * Requires a separator between 4-digit groups so long numeric IDs / nonces (16+ contiguous digits)
     * are not mistaken for PANs — avoids corrupting JSON bodies before downstream parsers run.
     */
    private static final Pattern CREDIT = Pattern.compile("\\b(?:\\d{4}[-\\s]){3}\\d{4}\\b");
    private static final Pattern IP_ADDR = Pattern.compile("\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b");
    private static final Pattern AUTH_TOKEN = Pattern.compile("(?i)\\b(?:bearer\\s+)?[A-Za-z0-9-_=]+\\.[A-Za-z0-9-_=]+\\.?[A-Za-z0-9-_.+/=]*\\b");
    private static final Pattern API_KEY = Pattern.compile("(?i)\\b(?:ak|sk|aws|stripe|secret|key|token)[_-][a-z0-9]{16,}\\b");

    @Override public String name() { return SentinelConstants.PLUGIN_PII_SCRUBBER; }
    @Override public Stage stage() { return Stage.PRE_PROCESS; }
    @Override public int order() { return 3; }

    @Override
    public MiddlewareFunc middleware() {
        return (ctx, next) -> {
            byte[] raw = ctx.getRawPayload();
            if (raw == null || raw.length == 0) {
                next.run();
                return;
            }
            String text = new String(raw, StandardCharsets.UTF_8);
            AtomicInteger count = new AtomicInteger();
            String scrubbed = scrub(text, count);
            
            if (count.get() > 0) {
                ctx.setRawPayload(scrubbed.getBytes(StandardCharsets.UTF_8));
                ctx.getMetadata().put(SentinelConstants.META_PII_SCRUB_COUNT, String.valueOf(count.get()));
            }
            next.run();
        };
    }

    private String scrub(String text, AtomicInteger count) {
        String s = hashMask(text, EMAIL, "EMAIL", count);
        s = hashMask(s, PHONE, "PHONE", count);
        s = hashMask(s, SSN, "SSN", count);
        s = hashMask(s, CREDIT, "CARD", count);
        s = hashMask(s, IP_ADDR, "IP", count);
        s = hashMask(s, AUTH_TOKEN, "TOKEN", count);
        s = hashMask(s, API_KEY, "API_KEY", count);
        return s;
    }

    /**
     * Replaces matches with a consistent hash of the value: [LABEL_hash]
     */
    private String hashMask(String in, Pattern p, String label, AtomicInteger count) {
        Matcher m = p.matcher(in);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            count.incrementAndGet();
            String val = m.group();
            String hash = sha256Short(val);
            m.appendReplacement(sb, Matcher.quoteReplacement("[" + label + "_" + hash + "]"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String sha256Short(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 6);
        } catch (NoSuchAlgorithmException e) {
            return "ERR";
        }
    }
}
