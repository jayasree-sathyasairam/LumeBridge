package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.*;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.params.SetParams;

import java.util.List;
import java.util.Map;

public class DistributedLockPlugin implements Plugin {

    private static final String RELEASE_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
            "  return redis.call('del', KEYS[1]) " +
            "else " +
            "  return 0 " +
            "end";

    private JedisPooled jedis;
    private long ttlSeconds;

    @Override public String name()  { return SentinelConstants.PLUGIN_DISTRIBUTED_LOCK; }
    @Override public Stage stage()  { return Stage.DEDUP; }
    @Override public int order()    { return 2; }

    @Override
    public void init(Map<String, String> config) {
        String host = config.getOrDefault(SentinelConstants.CORE_KEY_REDIS_HOST, SentinelConstants.DEFAULT_REDIS_HOST);
        int port = Integer.parseInt(config.getOrDefault(SentinelConstants.CORE_KEY_REDIS_PORT, SentinelConstants.DEFAULT_REDIS_PORT));
        this.ttlSeconds = Long.parseLong(config.getOrDefault(
                SentinelConstants.CORE_KEY_LOCK_TTL_SECONDS,
                Integer.toString(SentinelConstants.DEFAULT_LOCK_TTL_SECONDS)));
        this.jedis = new JedisPooled(host, port);
    }

    @Override
    public MiddlewareFunc middleware() {
        return (ctx, next) -> {
            String hash = ctx.getPayloadHash();
            String requestId = ctx.getRequestId();
            String lockKey = SentinelConstants.REDIS_LOCK_KEY_PREFIX + hash;

            String result = jedis.set(lockKey, requestId,
                    SetParams.setParams().nx().ex(ttlSeconds));

            if (result == null) {
                String holder = jedis.get(lockKey);
                ctx.setHttpStatus(SentinelConstants.HTTP_STATUS_CONFLICT);
                ctx.setStatus(SentinelConstants.STATUS_LOCKED);
                ctx.getMetadata().put(SentinelConstants.META_LOCK_KEY, lockKey);
                ctx.getMetadata().put(SentinelConstants.META_LOCK_HOLDER, holder != null ? holder : SentinelConstants.VAL_LOCK_HOLDER_UNKNOWN);
                return;
            }

            try {
                next.run();
                if (ctx.getHttpStatus() == SentinelConstants.HTTP_STATUS_OK) {
                    ctx.setStatus(SentinelConstants.STATUS_COMPLETED);
                }
            } finally {
                jedis.eval(RELEASE_LUA, List.of(lockKey), List.of(requestId));
            }
        };
    }

    @Override
    public void close() {
        if (jedis != null) jedis.close();
    }
}
