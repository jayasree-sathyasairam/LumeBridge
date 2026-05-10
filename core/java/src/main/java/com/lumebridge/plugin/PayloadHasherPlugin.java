package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.*;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public class PayloadHasherPlugin implements Plugin {

    @Override public String name()  { return SentinelConstants.PLUGIN_PAYLOAD_HASHER; }
    @Override public Stage stage()  { return Stage.DEDUP; }
    @Override public int order()    { return 1; }

    @Override
    public MiddlewareFunc middleware() {
        return (ctx, next) -> {
            byte[] payload = ctx.getRawPayload();
            if (payload == null || payload.length == 0) {
                ctx.setHttpStatus(SentinelConstants.HTTP_STATUS_BAD_REQUEST);
                ctx.setStatus(SentinelConstants.STATUS_ERROR);
                ctx.setError(new IllegalArgumentException(SentinelConstants.MSG_EMPTY_PAYLOAD_FOR_HASHER));
                return;
            }

            String hash = sha256Hex(payload);
            ctx.setPayloadHash(hash);
            ctx.setRequestId(hash.substring(0, 12) + "-" + System.nanoTime());

            next.run();
        };
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(data);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
