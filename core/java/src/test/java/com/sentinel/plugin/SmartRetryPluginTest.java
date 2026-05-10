package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.MiddlewareFunc;
import com.lumebridge.pipeline.Pipeline;
import com.lumebridge.pipeline.Plugin;
import com.lumebridge.pipeline.RequestContext;
import com.lumebridge.pipeline.Stage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SmartRetryPluginTest {

    @Test
    void retriesIOExceptionThenSucceeds() throws Exception {
        SmartRetryPlugin retry = new SmartRetryPlugin();
        retry.init(Map.of(
                SentinelConstants.CFG_KEY_MAX_RETRIES, "3",
                SentinelConstants.CFG_KEY_BASE_DELAY_MS, "1",
                SentinelConstants.CFG_KEY_MAX_DELAY_MS, "2"));

        AtomicInteger calls = new AtomicInteger();
        Plugin flaky = new Plugin() {
            @Override
            public String name() {
                return "flaky";
            }

            @Override
            public Stage stage() {
                return Stage.EXECUTE;
            }

            @Override
            public int order() {
                return 3;
            }

            @Override
            public MiddlewareFunc middleware() {
                return (ctx, next) -> {
                    if (calls.getAndIncrement() < 2) {
                        throw new RuntimeException(new IOException("transient"));
                    }
                    next.run();
                };
            }
        };

        Pipeline p = new Pipeline();
        p.register(retry);
        p.register(flaky);
        p.build();
        RequestContext ctx = new RequestContext("{}".getBytes());
        assertDoesNotThrow(() -> p.execute(ctx));
    }

    @Test
    void retriableSubstringGate() throws Exception {
        SmartRetryPlugin retry = new SmartRetryPlugin();
        retry.init(Map.of(
                SentinelConstants.CFG_KEY_MAX_RETRIES, "5",
                SentinelConstants.CFG_KEY_BASE_DELAY_MS, "1",
                SentinelConstants.CFG_KEY_MAX_DELAY_MS, "2",
                SentinelConstants.CFG_KEY_RETRIABLE_MESSAGE_SUBSTRINGS, "mysql"));

        AtomicInteger calls = new AtomicInteger();
        Plugin noisy = new Plugin() {
            @Override
            public String name() {
                return "noisy";
            }

            @Override
            public Stage stage() {
                return Stage.EXECUTE;
            }

            @Override
            public int order() {
                return 3;
            }

            @Override
            public MiddlewareFunc middleware() {
                return (ctx, next) -> {
                    calls.incrementAndGet();
                    throw new RuntimeException("plain error");
                };
            }
        };

        Pipeline p = new Pipeline();
        p.register(retry);
        p.register(noisy);
        p.build();
        RequestContext ctx = new RequestContext("{}".getBytes());
        assertThrows(RuntimeException.class, () -> p.execute(ctx));
        assertEquals(1, calls.get());
    }
}
