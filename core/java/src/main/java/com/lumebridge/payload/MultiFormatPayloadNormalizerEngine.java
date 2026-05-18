package com.lumebridge.payload;

import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.RequestContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Stateful normalizer: optional protobuf descriptor set from plugin YAML; MessagePack always canonicalized when CT/sniff says msgpack.
 */
public final class MultiFormatPayloadNormalizerEngine {

    private final ProtobufDescriptorRegistry protobufRegistry;
    private final String protobufMessageHeader;

    public MultiFormatPayloadNormalizerEngine(Map<String, String> config) {
        this.protobufMessageHeader = config.getOrDefault(
                SentinelConstants.CFG_KEY_PAYLOAD_PROTOBUF_MESSAGE_HEADER,
                SentinelConstants.HEADER_PROTOBUF_MESSAGE).trim();
        String descPath = config.getOrDefault(SentinelConstants.CFG_KEY_PAYLOAD_PROTOBUF_DESCRIPTOR_PATH, "").trim();
        ProtobufDescriptorRegistry reg = null;
        if (!descPath.isBlank()) {
            try {
                Path p = Path.of(descPath);
                if (!p.isAbsolute()) {
                    p = Path.of(System.getProperty("user.dir", ".")).resolve(p).normalize();
                }
                if (!Files.isRegularFile(p)) {
                    System.err.println("payload-normalizer: protobuf_descriptor_path not found: " + p.toAbsolutePath());
                } else {
                    reg = ProtobufDescriptorRegistry.load(p);
                }
            } catch (Exception e) {
                System.err.println("payload-normalizer: failed to load protobuf descriptors: " + e.getMessage());
            }
        }
        this.protobufRegistry = reg;
    }

    public void apply(RequestContext ctx) {
        byte[] raw = ctx.getRawPayload();
        String ct = ctx.getRequestHeader(SentinelConstants.HEADER_CONTENT_TYPE);
        PayloadMediaFamily family = PayloadFormatDetector.detect(ct, raw);
        try {
            byte[] next = switch (family) {
                case BINARY_OPAQUE -> {
                    ctx.getWarnings().add(SentinelConstants.WARN_PAYLOAD_NORMALIZER_PREFIX
                            + "opaque/binary Content-Type — body not canonicalized for hashing");
                    yield raw;
                }
                case JSON -> JsonCanonicalNormalizer.normalizeUtf8(raw);
                case XML -> XmlCanonicalNormalizer.normalizeUtf8(raw);
                case FORM_URLENCODED -> FormUrlEncodedNormalizer.normalizeUtf8(raw);
                case YAML -> YamlCanonicalNormalizer.normalizeUtf8(raw);
                case MSGPACK -> MsgPackCanonicalNormalizer.normalizeUtf8(raw);
                case PROTOBUF -> normalizeProtobuf(ctx, raw);
                case UNKNOWN -> normalizeSniffed(raw);
            };
            ctx.setRawPayload(next);
        } catch (Exception e) {
            ctx.getWarnings().add(SentinelConstants.WARN_PAYLOAD_NORMALIZER_PREFIX + e.getMessage());
        }
    }

    private byte[] normalizeProtobuf(RequestContext ctx, byte[] raw) {
        String msgName = ctx.getRequestHeader(protobufMessageHeader);
        if (protobufRegistry == null || msgName == null || msgName.isBlank()) {
            ctx.getWarnings().add(SentinelConstants.WARN_PAYLOAD_NORMALIZER_PREFIX
                    + "protobuf body requires protobuf_descriptor_path in config and header "
                    + protobufMessageHeader + " (fully-qualified message name)");
            return raw;
        }
        try {
            return ProtobufCanonicalNormalizer.normalizeUtf8(raw, msgName.strip(), protobufRegistry);
        } catch (Exception e) {
            ctx.getWarnings().add(SentinelConstants.WARN_PAYLOAD_NORMALIZER_PREFIX + "protobuf: " + e.getMessage());
            return raw;
        }
    }

    private static byte[] normalizeSniffed(byte[] raw) throws Exception {
        PayloadMediaFamily sn = PayloadFormatDetector.sniffBytes(raw);
        return switch (sn) {
            case JSON -> JsonCanonicalNormalizer.normalizeUtf8(raw);
            case XML -> XmlCanonicalNormalizer.normalizeUtf8(raw);
            case YAML -> YamlCanonicalNormalizer.normalizeUtf8(raw);
            case FORM_URLENCODED -> FormUrlEncodedNormalizer.normalizeUtf8(raw);
            case MSGPACK -> MsgPackCanonicalNormalizer.normalizeUtf8(raw);
            default -> raw;
        };
    }
}
