package com.lumebridge.pipeline;

import com.lumebridge.plugin.PayloadHasherPlugin;
import com.lumebridge.plugin.PayloadNormalizerPlugin;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PipelineExecuteTest {

    @Test
    void preProcessRunsBeforeDedupHasher() throws Exception {
        Pipeline p = new Pipeline();
        p.register(new PayloadNormalizerPlugin());
        p.register(new PayloadHasherPlugin());
        p.build();

        byte[] raw = "{\"b\":1,\"a\":2}".getBytes();
        RequestContext ctx = new RequestContext(raw);
        p.execute(ctx);

        assertNotNull(ctx.getPayloadHash());
        assertEquals(64, ctx.getPayloadHash().length());
        String normalized = new String(ctx.getRawPayload(), StandardCharsets.UTF_8);
        assertTrue(normalized.indexOf("\"a\"") < normalized.indexOf("\"b\""));
    }

    @Test
    void emptyHasherPayloadSetsBadRequest() throws Exception {
        Pipeline p = new Pipeline();
        p.register(new PayloadHasherPlugin());
        p.build();
        RequestContext ctx = new RequestContext(new byte[0]);
        p.execute(ctx);
        assertEquals(400, ctx.getHttpStatus());
    }

    @Test
    void tailMiddlewareRunsAfterHasher() throws Exception {
        AtomicBoolean ran = new AtomicBoolean();
        Pipeline p = new Pipeline();
        p.register(new PayloadHasherPlugin());
        p.register(new Plugin() {
            @Override
            public String name() {
                return "noop";
            }

            @Override
            public Stage stage() {
                return Stage.DEDUP;
            }

            @Override
            public int order() {
                return 3;
            }

            @Override
            public MiddlewareFunc middleware() {
                return (ctx, next) -> {
                    next.run();
                    ran.set(true);
                };
            }
        });
        p.build();
        RequestContext ctx = new RequestContext("{\"x\":1}".getBytes());
        p.execute(ctx);
        assertNotNull(ctx.getPayloadHash());
        assertTrue(ran.get());
    }
}
