package com.lumebridge.payload;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.ArrayValue;
import org.msgpack.value.BinaryValue;
import org.msgpack.value.MapValue;
import org.msgpack.value.Value;
import org.msgpack.value.ValueType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Decodes MessagePack → structured tree → sorted JSON UTF-8 for stable hashing. */
public final class MsgPackCanonicalNormalizer {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private MsgPackCanonicalNormalizer() {}

    public static byte[] normalizeUtf8(byte[] raw) throws Exception {
        try (MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(raw)) {
            Value v = unpacker.unpackValue();
            if (unpacker.hasNext()) {
                throw new IllegalArgumentException("trailing MessagePack bytes after root value");
            }
            Object tree = msgPackToStructured(v);
            JsonElement je = StructuredPayloadConverter.toJsonElement(tree);
            return GSON.toJson(je).getBytes(StandardCharsets.UTF_8);
        }
    }

    static Object msgPackToStructured(Value v) throws IOException {
        ValueType t = v.getValueType();
        if (t == ValueType.NIL) {
            return null;
        }
        if (t == ValueType.BOOLEAN) {
            return v.asBooleanValue().getBoolean();
        }
        if (t == ValueType.INTEGER) {
            return v.asIntegerValue().asLong();
        }
        if (t == ValueType.FLOAT) {
            return v.asFloatValue().toDouble();
        }
        if (t == ValueType.STRING) {
            return v.asStringValue().asString();
        }
        if (t == ValueType.BINARY) {
            BinaryValue b = v.asBinaryValue();
            return bytesAsHexLiteral(b.asByteArray());
        }
        if (t == ValueType.ARRAY) {
            ArrayValue arr = v.asArrayValue();
            List<Object> out = new ArrayList<>();
            for (Value e : arr) {
                out.add(msgPackToStructured(e));
            }
            return out;
        }
        if (t == ValueType.MAP) {
            MapValue mv = v.asMapValue();
            Map<Object, Object> out = new LinkedHashMap<>();
            for (Map.Entry<Value, Value> e : mv.entrySet()) {
                Object key = msgPackToStructured(e.getKey());
                out.put(key, msgPackToStructured(e.getValue()));
            }
            return out;
        }
        try (MessageBufferPacker p = MessagePack.newDefaultBufferPacker()) {
            p.packValue(v);
            return bytesAsHexLiteral(p.toByteArray());
        }
    }

    private static String bytesAsHexLiteral(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2 + 4);
        sb.append("hex:");
        for (byte b : data) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
