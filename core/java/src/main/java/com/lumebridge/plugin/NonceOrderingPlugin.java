package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.MiddlewareFunc;
import com.lumebridge.pipeline.Plugin;
import com.lumebridge.pipeline.Stage;
import com.lumebridge.util.JsonBody;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.params.SetParams;

import java.util.Map;

/**
 * Optional strict ordering: rejects when {@code nonce} is not strictly greater than the last seen nonce for this payload hash (Redis).
 */
public class NonceOrderingPlugin implements Plugin {

    private JedisPooled jedis;

    @Override public String name() { return SentinelConstants.PLUGIN_NONCE_ORDERING; }
    @Override public Stage stage() { return Stage.DEDUP; }
    @Override public int order() { return 4; }

    @Override
    public void init(Map<String, String> config) {
        String host = config.getOrDefault(SentinelConstants.CORE_KEY_REDIS_HOST, SentinelConstants.DEFAULT_REDIS_HOST);
        int port = Integer.parseInt(config.getOrDefault(SentinelConstants.CORE_KEY_REDIS_PORT, SentinelConstants.DEFAULT_REDIS_PORT));
        this.jedis = new JedisPooled(host, port);
    }

    @Override
    public MiddlewareFunc middleware() {
        return (ctx, next) -> {
            var body = JsonBody.tryParse(ctx.getRawPayload());
            if (!hasNonce(body)) {
                next.run();
                return;
            }
            long nonce = body.get(SentinelConstants.JSON_FIELD_NONCE).getAsLong();
            String key = SentinelConstants.REDIS_NONCE_KEY_PREFIX + ctx.getPayloadHash();
            String prev = jedis.get(key);
            if (isNonceOutOfOrder(prev, nonce)) {
                rejectNonce(ctx, prev, nonce);
                return;
            }
            jedis.set(key, String.valueOf(nonce), SetParams.setParams().ex(SentinelConstants.NONCE_KEY_TTL_SECONDS));
            next.run();
        };
    }

    private boolean hasNonce(com.google.gson.JsonElement body) {
        return body != null && body.isJsonObject()
               && body.getAsJsonObject().has(SentinelConstants.JSON_FIELD_NONCE);
    }

    private boolean isNonceOutOfOrder(String prev, long incoming) {
        if (prev == null || prev.isEmpty()) {
            return false;
        }
        long prevValue = Long.parseLong(prev);
        return incoming <= prevValue;
    }

    private void rejectNonce(com.lumebridge.pipeline.RequestContext ctx, String prev, long incoming) {
        ctx.setHttpStatus(SentinelConstants.HTTP_STATUS_CONFLICT);
        ctx.setStatus(SentinelConstants.STATUS_NONCE_REJECTED);
        ctx.getMetadata().put(SentinelConstants.META_CACHED_NONCE, prev);
        ctx.getMetadata().put(SentinelConstants.META_INCOMING_NONCE, String.valueOf(incoming));
    }

    @Override
    public void close() {
        if (jedis != null) {
            jedis.close();
        }
    }
}
