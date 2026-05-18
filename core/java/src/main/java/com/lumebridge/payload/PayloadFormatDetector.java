package com.lumebridge.payload;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Uses {@code Content-Type} first, then lightweight sniffing (prefix / UTF-8 probe).
 */
public final class PayloadFormatDetector {

    private PayloadFormatDetector() {}

    public static String primaryMime(String contentTypeHeader) {
        if (contentTypeHeader == null || contentTypeHeader.isBlank()) {
            return "";
        }
        String s = contentTypeHeader.trim().split(";")[0].trim().toLowerCase(Locale.ROOT);
        return s;
    }

    public static PayloadMediaFamily detect(String contentTypeHeader, byte[] raw) {
        String ct = primaryMime(contentTypeHeader);
        if (ct.contains("json")) {
            return PayloadMediaFamily.JSON;
        }
        if (ct.contains("yaml")) {
            return PayloadMediaFamily.YAML;
        }
        if (ct.contains("x-www-form-urlencoded")) {
            return PayloadMediaFamily.FORM_URLENCODED;
        }
        if (ct.contains("xml") || ct.endsWith("+xml")) {
            return PayloadMediaFamily.XML;
        }
        if (ct.contains("msgpack")) {
            return PayloadMediaFamily.MSGPACK;
        }
        if (ct.contains("protobuf") || ct.contains("x-protobuf")) {
            return PayloadMediaFamily.PROTOBUF;
        }
        if (ct.contains("octet-stream")) {
            return PayloadMediaFamily.BINARY_OPAQUE;
        }

        return sniffBytes(raw);
    }

    static PayloadMediaFamily sniffBytes(byte[] raw) {
        if (raw == null || raw.length == 0) {
            return PayloadMediaFamily.UNKNOWN;
        }
        int i = skipBomAndWs(raw, 0);
        if (i >= raw.length) {
            return PayloadMediaFamily.UNKNOWN;
        }
        byte b = raw[i];
        if (b == '{' || b == '[') {
            return PayloadMediaFamily.JSON;
        }
        if (b == '<') {
            return PayloadMediaFamily.XML;
        }
        String probe = new String(raw, i, Math.min(raw.length - i, 512), StandardCharsets.UTF_8);
        String probeLc = probe.toLowerCase(Locale.ROOT);
        if (probeLc.startsWith("---") || probeLc.contains(": ") || probeLc.startsWith("- ")) {
            return PayloadMediaFamily.YAML;
        }
        if (looksLikeFormBody(raw, i)) {
            return PayloadMediaFamily.FORM_URLENCODED;
        }
        if (looksLikeMsgPack(raw, i)) {
            return PayloadMediaFamily.MSGPACK;
        }
        return PayloadMediaFamily.UNKNOWN;
    }

    /**
     * Heuristic: first byte often matches msgpack fixmap/fixarray/fixstr header ranges when not textual JSON/XML/YAML.
     */
    private static boolean looksLikeMsgPack(byte[] raw, int offset) {
        if (offset >= raw.length) {
            return false;
        }
        int b = raw[offset] & 0xFF;
        return (b >= 0x80 && b <= 0x8f)
                || (b >= 0x90 && b <= 0x9f)
                || (b >= 0xa0 && b <= 0xbf)
                || b == 0xc4 || b == 0xc5 || b == 0xc6
                || (b >= 0xcc && b <= 0xd3)
                || (b >= 0xd9 && b <= 0xdb)
                || (b >= 0xdc && b <= 0xdf)
                || (b >= 0xe0 && b <= 0xff);
    }

    static int skipBomAndWs(byte[] raw, int start) {
        int i = start;
        if (raw.length >= i + 3 && raw[i] == (byte) 0xEF && raw[i + 1] == (byte) 0xBB && raw[i + 2] == (byte) 0xBF) {
            i += 3;
        }
        while (i < raw.length && (raw[i] == ' ' || raw[i] == '\t' || raw[i] == '\r' || raw[i] == '\n')) {
            i++;
        }
        return i;
    }

    private static boolean looksLikeFormBody(byte[] raw, int offset) {
        boolean sawEq = false;
        boolean sawAmp = false;
        for (int j = offset; j < raw.length && j < offset + 4096; j++) {
            byte b = raw[j];
            if (b == '=') {
                sawEq = true;
            } else if (b == '&') {
                sawAmp = true;
            }
            if (b == '{' || b == '[' || b == '<') {
                return false;
            }
        }
        return sawEq && (sawAmp || raw.length - offset < 2048);
    }
}
