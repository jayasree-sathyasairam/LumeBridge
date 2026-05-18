package com.lumebridge.payload;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;

import java.nio.charset.StandardCharsets;

/** Parses protobuf wire bytes using a registry-backed descriptor; emits sorted JSON for hashing. */
public final class ProtobufCanonicalNormalizer {

    private ProtobufCanonicalNormalizer() {}

    public static byte[] normalizeUtf8(byte[] raw, String messageFullName, ProtobufDescriptorRegistry registry)
            throws InvalidProtocolBufferException {
        Descriptors.Descriptor d = registry.find(messageFullName);
        if (d == null) {
            throw new InvalidProtocolBufferException("unknown protobuf message type: " + messageFullName);
        }
        DynamicMessage msg = DynamicMessage.parseFrom(d, raw);
        String js = JsonFormat.printer()
                .preservingProtoFieldNames()
                .includingDefaultValueFields()
                .print(msg);
        return JsonCanonicalNormalizer.normalizeUtf8(js.getBytes(StandardCharsets.UTF_8));
    }
}
