package com.sentinel.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.plugin.PayloadNormalizerPlugin;
import com.lumebridge.pipeline.RequestContext;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayloadNormalizerPluginTest {

    @Test
    void sortsTopLevelKeys() throws Exception {
        PayloadNormalizerPlugin n = new PayloadNormalizerPlugin();
        n.init(java.util.Map.of());
        RequestContext ctx = new RequestContext("{\"z\":1,\"a\":2}".getBytes(StandardCharsets.UTF_8));
        ctx.putRequestHeader(SentinelConstants.HEADER_CONTENT_TYPE, "application/json");
        n.middleware().apply(ctx, () -> { });
        String out = new String(ctx.getRawPayload(), StandardCharsets.UTF_8);
        assertTrue(out.indexOf("\"a\"") < out.indexOf("\"z\""));
    }

    @Test
    void malformedJsonAddsWarning() throws Exception {
        PayloadNormalizerPlugin n = new PayloadNormalizerPlugin();
        n.init(java.util.Map.of());
        byte[] raw = "{not-valid-json".getBytes(StandardCharsets.UTF_8);
        RequestContext ctx = new RequestContext(raw);
        ctx.putRequestHeader(SentinelConstants.HEADER_CONTENT_TYPE, "application/json");
        n.middleware().apply(ctx, () -> { });
        assertEquals("{not-valid-json", new String(ctx.getRawPayload(), StandardCharsets.UTF_8));
        assertTrue(ctx.getWarnings().stream().anyMatch(w -> w.startsWith(SentinelConstants.WARN_PAYLOAD_NORMALIZER_PREFIX)));
    }

    @Test
    void jsonArrayLeavesPayloadUnchangedAndNoWarning() throws Exception {
        PayloadNormalizerPlugin n = new PayloadNormalizerPlugin();
        n.init(java.util.Map.of());
        String body = "[1,2]";
        RequestContext ctx = new RequestContext(body.getBytes(StandardCharsets.UTF_8));
        ctx.putRequestHeader(SentinelConstants.HEADER_CONTENT_TYPE, "application/json");
        n.middleware().apply(ctx, () -> { });
        assertEquals(body, new String(ctx.getRawPayload(), StandardCharsets.UTF_8));
        assertTrue(ctx.getWarnings().isEmpty());
    }

    @Test
    void canonicalizesXmlWithSortedAttributesAndStableSiblingOrder() throws Exception {
        PayloadNormalizerPlugin n = new PayloadNormalizerPlugin();
        n.init(java.util.Map.of());
        String xml = "<root z=\"2\" a=\"1\"><b/><a/></root>";
        RequestContext ctx = new RequestContext(xml.getBytes(StandardCharsets.UTF_8));
        ctx.putRequestHeader(SentinelConstants.HEADER_CONTENT_TYPE, "application/xml");
        n.middleware().apply(ctx, () -> { });
        String out = new String(ctx.getRawPayload(), StandardCharsets.UTF_8);
        assertTrue(out.indexOf("a=\"") < out.indexOf("z=\""));
        assertTrue(out.indexOf("<a></a>") < out.indexOf("<b></b>"));
    }

    @Test
    void canonicalizesUrlEncodedSortedKeys() throws Exception {
        PayloadNormalizerPlugin n = new PayloadNormalizerPlugin();
        n.init(java.util.Map.of());
        RequestContext ctx = new RequestContext("z=3&a=1&a=2".getBytes(StandardCharsets.UTF_8));
        ctx.putRequestHeader(SentinelConstants.HEADER_CONTENT_TYPE, "application/x-www-form-urlencoded");
        n.middleware().apply(ctx, () -> { });
        assertEquals("a=2&z=3", new String(ctx.getRawPayload(), StandardCharsets.UTF_8));
    }

    @Test
    void canonicalizesYamlToSortedJsonObject() throws Exception {
        PayloadNormalizerPlugin n = new PayloadNormalizerPlugin();
        n.init(java.util.Map.of());
        String yaml = "z: 1\na: 2\n";
        RequestContext ctx = new RequestContext(yaml.getBytes(StandardCharsets.UTF_8));
        ctx.putRequestHeader(SentinelConstants.HEADER_CONTENT_TYPE, "application/yaml");
        n.middleware().apply(ctx, () -> { });
        String out = new String(ctx.getRawPayload(), StandardCharsets.UTF_8);
        assertTrue(out.indexOf("\"a\"") < out.indexOf("\"z\""));
    }

    @Test
    void protobufWithoutDescriptorAddsWarningAndLeavesBody() throws Exception {
        PayloadNormalizerPlugin n = new PayloadNormalizerPlugin();
        n.init(java.util.Map.of());
        byte[] raw = new byte[] {0x0a, 0x0b};
        RequestContext ctx = new RequestContext(raw);
        ctx.putRequestHeader(SentinelConstants.HEADER_CONTENT_TYPE, "application/x-protobuf");
        n.middleware().apply(ctx, () -> { });
        assertEquals(raw.length, ctx.getRawPayload().length);
        assertTrue(ctx.getWarnings().stream().anyMatch(w -> w.contains("protobuf_descriptor_path")));
    }

    @Test
    void octetStreamAddsOpaqueBinaryWarning() throws Exception {
        PayloadNormalizerPlugin n = new PayloadNormalizerPlugin();
        n.init(java.util.Map.of());
        byte[] raw = new byte[] {0x0a, 0x0b};
        RequestContext ctx = new RequestContext(raw);
        ctx.putRequestHeader(SentinelConstants.HEADER_CONTENT_TYPE, "application/octet-stream");
        n.middleware().apply(ctx, () -> { });
        assertEquals(raw.length, ctx.getRawPayload().length);
        assertTrue(ctx.getWarnings().stream().anyMatch(w -> w.contains("opaque/binary")));
    }

    @Test
    void canonicalizesMsgPackMapToSortedJsonKeys() throws Exception {
        PayloadNormalizerPlugin n = new PayloadNormalizerPlugin();
        n.init(java.util.Map.of());
        try (org.msgpack.core.MessageBufferPacker p = org.msgpack.core.MessagePack.newDefaultBufferPacker()) {
            p.packMapHeader(2);
            p.packString("z").packInt(3);
            p.packString("a").packInt(1);
            byte[] raw = p.toByteArray();
            RequestContext ctx = new RequestContext(raw);
            ctx.putRequestHeader(SentinelConstants.HEADER_CONTENT_TYPE, "application/msgpack");
            n.middleware().apply(ctx, () -> { });
            String out = new String(ctx.getRawPayload(), StandardCharsets.UTF_8);
            assertTrue(out.indexOf("\"a\"") < out.indexOf("\"z\""));
            assertTrue(ctx.getWarnings().isEmpty());
        }
    }
}
