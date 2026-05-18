package com.sentinel.payload;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.InvalidProtocolBufferException;
import com.lumebridge.payload.ProtobufCanonicalNormalizer;
import com.lumebridge.payload.ProtobufDescriptorRegistry;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtobufCanonicalNormalizerTest {

    @Test
    void emitsSortedJsonFieldNames() throws Exception {
        DescriptorProtos.FileDescriptorProto fileProto =
                DescriptorProtos.FileDescriptorProto.newBuilder()
                        .setSyntax("proto3")
                        .setPackage("te.pkg")
                        .setName("t.proto")
                        .addMessageType(
                                DescriptorProtos.DescriptorProto.newBuilder()
                                        .setName("M")
                                        .addField(
                                                DescriptorProtos.FieldDescriptorProto.newBuilder()
                                                        .setName("z_field")
                                                        .setNumber(2)
                                                        .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                                                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32)
                                                        .build())
                                        .addField(
                                                DescriptorProtos.FieldDescriptorProto.newBuilder()
                                                        .setName("a_field")
                                                        .setNumber(1)
                                                        .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                                                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32)
                                                        .build())
                                        .build())
                        .build();

        Descriptors.FileDescriptor fd =
                Descriptors.FileDescriptor.buildFrom(fileProto, new Descriptors.FileDescriptor[0]);
        ProtobufDescriptorRegistry reg = ProtobufDescriptorRegistry.fromFileDescriptors(fd);
        Descriptors.Descriptor md = reg.find("te.pkg.M");

        DynamicMessage dm =
                DynamicMessage.newBuilder(md)
                        .setField(md.findFieldByName("z_field"), 3)
                        .setField(md.findFieldByName("a_field"), 1)
                        .build();
        byte[] raw = dm.toByteArray();

        byte[] norm = ProtobufCanonicalNormalizer.normalizeUtf8(raw, "te.pkg.M", reg);
        String s = new String(norm, StandardCharsets.UTF_8);
        assertTrue(s.indexOf("a_field") < s.indexOf("z_field"));
    }

    @Test
    void unknownMessageNameFails() throws Exception {
        DescriptorProtos.FileDescriptorProto fileProto =
                DescriptorProtos.FileDescriptorProto.newBuilder()
                        .setSyntax("proto3")
                        .setPackage("x")
                        .setName("x.proto")
                        .addMessageType(
                                DescriptorProtos.DescriptorProto.newBuilder()
                                        .setName("Y")
                                        .addField(
                                                DescriptorProtos.FieldDescriptorProto.newBuilder()
                                                        .setName("n")
                                                        .setNumber(1)
                                                        .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL)
                                                        .setType(DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32)
                                                        .build())
                                        .build())
                        .build();

        Descriptors.FileDescriptor fd =
                Descriptors.FileDescriptor.buildFrom(fileProto, new Descriptors.FileDescriptor[0]);
        ProtobufDescriptorRegistry reg = ProtobufDescriptorRegistry.fromFileDescriptors(fd);
        byte[] raw = new byte[] {0x08, 0x07};

        assertThrows(
                InvalidProtocolBufferException.class,
                () -> ProtobufCanonicalNormalizer.normalizeUtf8(raw, "x.NotThere", reg));
    }
}
