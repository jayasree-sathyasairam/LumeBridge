package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.RequestContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CircuitBreakerPluginTest {

    @Test
    void opensCircuitAfterThresholdFailures() throws Exception {
        CircuitBreakerPlugin cb = new CircuitBreakerPlugin();
        cb.init(java.util.Map.of(
                SentinelConstants.CFG_KEY_FAILURE_THRESHOLD, "2",
                SentinelConstants.CFG_KEY_RECOVERY_TIMEOUT_MS, "60000"));

        RequestContext first = new RequestContext("{}".getBytes());
        cb.middleware().apply(first, () -> {
            first.setHttpStatus(SentinelConstants.HTTP_STATUS_INTERNAL_ERROR);
        });
        RequestContext second = new RequestContext("{}".getBytes());
        cb.middleware().apply(second, () -> {
            second.setHttpStatus(SentinelConstants.HTTP_STATUS_INTERNAL_ERROR);
        });
        RequestContext third = new RequestContext("{}".getBytes());
        cb.middleware().apply(third, () -> {
            third.setHttpStatus(SentinelConstants.HTTP_STATUS_OK);
        });
        assertEquals(SentinelConstants.HTTP_STATUS_SERVICE_UNAVAILABLE, third.getHttpStatus());
        assertEquals(SentinelConstants.STATUS_CIRCUIT_OPEN, third.getStatus());
    }

    @Test
    void recoversAfterTimeout() throws Exception {
        CircuitBreakerPlugin cb = new CircuitBreakerPlugin();
        cb.init(java.util.Map.of(
                SentinelConstants.CFG_KEY_FAILURE_THRESHOLD, "1",
                SentinelConstants.CFG_KEY_RECOVERY_TIMEOUT_MS, "40"));

        RequestContext failCtx = new RequestContext("{}".getBytes());
        cb.middleware().apply(failCtx, () ->
                failCtx.setHttpStatus(SentinelConstants.HTTP_STATUS_INTERNAL_ERROR));

        RequestContext blocked = new RequestContext("{}".getBytes());
        cb.middleware().apply(blocked, () -> {
            throw new AssertionError("circuit open");
        });
        assertEquals(SentinelConstants.HTTP_STATUS_SERVICE_UNAVAILABLE, blocked.getHttpStatus());

        Thread.sleep(50);

        RequestContext ok = new RequestContext("{}".getBytes());
        cb.middleware().apply(ok, () -> ok.setHttpStatus(SentinelConstants.HTTP_STATUS_OK));
        assertEquals(SentinelConstants.HTTP_STATUS_OK, ok.getHttpStatus());
    }
}
