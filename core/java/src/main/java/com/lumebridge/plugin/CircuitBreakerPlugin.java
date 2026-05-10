package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.MiddlewareFunc;
import com.lumebridge.pipeline.Plugin;
import com.lumebridge.pipeline.RequestContext;
import com.lumebridge.pipeline.Stage;

import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Counts downstream failures after the inner chain runs; opens the circuit to fast-fail with 503 until the recovery window.
 * Failures are {@code httpStatus >= 500} or a recorded {@link RequestContext#getError()}.
 */
public class CircuitBreakerPlugin implements Plugin {

    private enum State { CLOSED, OPEN, HALF_OPEN }

    private final ReentrantLock lock = new ReentrantLock();
    private State state = State.CLOSED;
    private int failures;
    private long openedAtNanos;
    private int failureThreshold = SentinelConstants.DEFAULT_FAILURE_THRESHOLD;
    private long recoveryTimeoutNanos = SentinelConstants.DEFAULT_RECOVERY_TIMEOUT_MS * 1_000_000L;

    @Override public String name() { return SentinelConstants.PLUGIN_CIRCUIT_BREAKER; }
    @Override public Stage stage() { return Stage.EXECUTE; }
    @Override public int order() { return 1; }

    @Override
    public void init(Map<String, String> config) {
        this.failureThreshold = Integer.parseInt(config.getOrDefault(
                SentinelConstants.CFG_KEY_FAILURE_THRESHOLD,
                Integer.toString(SentinelConstants.DEFAULT_FAILURE_THRESHOLD)));
        long recoveryMs = Long.parseLong(config.getOrDefault(
                SentinelConstants.CFG_KEY_RECOVERY_TIMEOUT_MS,
                Integer.toString(SentinelConstants.DEFAULT_RECOVERY_TIMEOUT_MS)));
        this.recoveryTimeoutNanos = recoveryMs * 1_000_000;
    }

    @Override
    public MiddlewareFunc middleware() {
        return (ctx, next) -> {
            if (!allow()) {
                ctx.setHttpStatus(SentinelConstants.HTTP_STATUS_SERVICE_UNAVAILABLE);
                ctx.setStatus(SentinelConstants.STATUS_CIRCUIT_OPEN);
                ctx.getMetadata().put(SentinelConstants.META_CIRCUIT, SentinelConstants.VAL_CIRCUIT_STATE_OPEN);
                return;
            }
            try {
                next.run();
            } catch (Exception e) {
                onFailure();
                throw e;
            }
            if (isDownstreamFailure(ctx)) {
                onFailure();
            } else {
                onSuccess();
            }
        };
    }

    private static boolean isDownstreamFailure(RequestContext ctx) {
        if (ctx.getHttpStatus() >= SentinelConstants.HTTP_STATUS_INTERNAL_ERROR) {
            return true;
        }
        return ctx.getError() != null;
    }

    private boolean allow() {
        lock.lock();
        try {
            long now = System.nanoTime();
            return switch (state) {
                case CLOSED -> true;
                case OPEN -> {
                    if (now - openedAtNanos >= recoveryTimeoutNanos) {
                        state = State.HALF_OPEN;
                        yield true;
                    }
                    yield false;
                }
                case HALF_OPEN -> true;
            };
        } finally {
            lock.unlock();
        }
    }

    private void onSuccess() {
        lock.lock();
        try {
            failures = 0;
            state = State.CLOSED;
        } finally {
            lock.unlock();
        }
    }

    private void onFailure() {
        lock.lock();
        try {
            failures++;
            if (failures >= failureThreshold) {
                state = State.OPEN;
                openedAtNanos = System.nanoTime();
            }
        } finally {
            lock.unlock();
        }
    }
}
