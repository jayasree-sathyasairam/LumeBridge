package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.RequestContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@Tag("integration")
class RedisPluginsIT {

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofMinutes(1));

    private Map<String, String> redisCore() {
        return Map.of(
                SentinelConstants.CORE_KEY_REDIS_HOST, REDIS.getHost(),
                SentinelConstants.CORE_KEY_REDIS_PORT, String.valueOf(REDIS.getMappedPort(6379)));
    }

    @Test
    void distributedLockSecondCallerGetsConflictWhileHeld() throws Exception {
        DistributedLockPlugin lock = new DistributedLockPlugin();
        lock.init(redisCore());

        CountDownLatch holderInside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Exception> bgErr = new AtomicReference<>();

        RequestContext holderCtx = new RequestContext("{}".getBytes());
        holderCtx.setPayloadHash("shared-lock-hash");
        holderCtx.setRequestId("holder-req");

        Thread bg = new Thread(() -> {
            try {
                lock.middleware().apply(holderCtx, () -> {
                    holderInside.countDown();
                    try {
                        assertTrue(release.await(10, TimeUnit.SECONDS));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    }
                    holderCtx.setHttpStatus(SentinelConstants.HTTP_STATUS_OK);
                });
            } catch (Exception e) {
                bgErr.set(e);
            }
        });
        bg.start();
        assertTrue(holderInside.await(10, TimeUnit.SECONDS));

        RequestContext waiter = new RequestContext("{}".getBytes());
        waiter.setPayloadHash("shared-lock-hash");
        waiter.setRequestId("waiter-req");
        lock.middleware().apply(waiter, () -> {
            throw new AssertionError("waiter should not acquire lock");
        });
        assertEquals(SentinelConstants.HTTP_STATUS_CONFLICT, waiter.getHttpStatus());
        assertEquals(SentinelConstants.STATUS_LOCKED, waiter.getStatus());

        release.countDown();
        bg.join(10_000);
        assertFalse(bg.isAlive());
        if (bgErr.get() != null) {
            throw bgErr.get();
        }
        lock.close();
    }

    @Test
    void nonceOrderingRejectsNonIncreasingNonce() throws Exception {
        NonceOrderingPlugin nonce = new NonceOrderingPlugin();
        nonce.init(redisCore());

        String hash = "nh-" + java.util.UUID.randomUUID();
        String body = "{\"nonce\":1}";
        RequestContext ctx1 = new RequestContext(body.getBytes());
        ctx1.setPayloadHash(hash);
        nonce.middleware().apply(ctx1, () -> { });
        assertEquals(SentinelConstants.HTTP_STATUS_OK, ctx1.getHttpStatus());

        RequestContext ctx2 = new RequestContext(body.getBytes());
        ctx2.setPayloadHash(hash);
        nonce.middleware().apply(ctx2, () -> {
            throw new AssertionError("duplicate nonce should short-circuit");
        });
        assertEquals(SentinelConstants.HTTP_STATUS_CONFLICT, ctx2.getHttpStatus());
        assertEquals(SentinelConstants.STATUS_NONCE_REJECTED, ctx2.getStatus());

        RequestContext ctx3 = new RequestContext("{\"nonce\":2}".getBytes());
        ctx3.setPayloadHash(hash);
        nonce.middleware().apply(ctx3, () -> { });
        assertEquals(SentinelConstants.HTTP_STATUS_OK, ctx3.getHttpStatus());

        nonce.close();
    }
}
