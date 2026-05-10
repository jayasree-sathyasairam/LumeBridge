package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.MiddlewareFunc;
import com.lumebridge.pipeline.Plugin;
import com.lumebridge.pipeline.Stage;
import redis.clients.jedis.JedisPooled;

import java.util.Map;

/**
 * Enforces per-client rate limits using Redis.
 * Uses the authenticated user ID from metadata.
 */
public class ClientQuotaPlugin implements Plugin {

    private JedisPooled jedis;
    private int limitPerMinute = 60; // Default

    @Override public String name() { return SentinelConstants.PLUGIN_CLIENT_QUOTA; }
    @Override public Stage stage() { return Stage.PRE_PROCESS; }
    @Override public int order() { return 5; } // After Auth and Safety

    @Override
    public void init(Map<String, String> config) {
        String host = config.getOrDefault(SentinelConstants.CORE_KEY_REDIS_HOST, SentinelConstants.DEFAULT_REDIS_HOST);
        int port = Integer.parseInt(config.getOrDefault(SentinelConstants.CORE_KEY_REDIS_PORT, SentinelConstants.DEFAULT_REDIS_PORT));
        this.jedis = new JedisPooled(host, port);
        this.limitPerMinute = Integer.parseInt(config.getOrDefault("limit_per_minute", "60"));
    }

    @Override
    public MiddlewareFunc middleware() {
        return (ctx, next) -> {
            String userId = ctx.getMetadata().get(SentinelConstants.META_AUTH_USER_ID);
            if (userId == null) {
                // If no auth, we might skip or use IP. For now, just proceed if skip_auth is enabled.
                next.run();
                return;
            }

            String key = "quota:" + userId + ":" + (System.currentTimeMillis() / 60000);
            long current = jedis.incr(key);
            if (current == 1) {
                jedis.expire(key, 60);
            }

            if (current > limitPerMinute) {
                ctx.setHttpStatus(429); // Too Many Requests
                ctx.setStatus(SentinelConstants.STATUS_ERROR);
                ctx.setError(new RuntimeException("Rate limit exceeded. Limit is " + limitPerMinute + " req/min."));
                return;
            }

            next.run();
        };
    }

    @Override
    public void close() {
        if (jedis != null) jedis.close();
    }
}
