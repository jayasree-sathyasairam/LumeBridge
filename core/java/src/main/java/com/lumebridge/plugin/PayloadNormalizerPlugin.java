package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.payload.MultiFormatPayloadNormalizerEngine;
import com.lumebridge.pipeline.MiddlewareFunc;
import com.lumebridge.pipeline.Plugin;
import com.lumebridge.pipeline.Stage;

import java.util.Map;

/**
 * V2 P0: canonicalizes bodies per {@code Content-Type} (JSON, XML, YAML, form-urlencoded, MessagePack,
 * optional protobuf via descriptor set + {@link SentinelConstants#HEADER_PROTOBUF_MESSAGE}) so hashing stays stable.
 */
public class PayloadNormalizerPlugin implements Plugin {

    /** Default engine until {@link #init(Map)} runs (e.g. bare {@link Pipeline} tests). */
    private MultiFormatPayloadNormalizerEngine engine = new MultiFormatPayloadNormalizerEngine(Map.of());

    @Override public String name() { return SentinelConstants.PLUGIN_PAYLOAD_NORMALIZER; }
    @Override public Stage stage() { return Stage.PRE_PROCESS; }
    @Override public int order() { return 2; }

    @Override
    public void init(Map<String, String> config) {
        this.engine = new MultiFormatPayloadNormalizerEngine(config);
    }

    @Override
    public MiddlewareFunc middleware() {
        return (ctx, next) -> {
            engine.apply(ctx);
            next.run();
        };
    }
}
