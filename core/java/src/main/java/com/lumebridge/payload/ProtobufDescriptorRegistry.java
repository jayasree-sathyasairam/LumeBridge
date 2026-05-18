package com.lumebridge.payload;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads a protobuf {@link DescriptorProtos.FileDescriptorSet} for {@link ProtobufCanonicalNormalizer}. */
public final class ProtobufDescriptorRegistry {

    private final Map<String, Descriptors.Descriptor> typesByFullName = new HashMap<>();

    private ProtobufDescriptorRegistry() {}

    public static ProtobufDescriptorRegistry load(Path descriptorSetFile) throws IOException {
        byte[] bytes = Files.readAllBytes(descriptorSetFile);
        return parse(bytes);
    }

    /** Parses an in-memory descriptor set (tests / tooling). */
    public static ProtobufDescriptorRegistry parse(byte[] fileDescriptorSetBytes)
            throws IOException {
        DescriptorProtos.FileDescriptorSet set;
        try {
            set = DescriptorProtos.FileDescriptorSet.parseFrom(fileDescriptorSetBytes);
        } catch (Exception e) {
            throw new IOException("invalid protobuf descriptor set", e);
        }
        try {
            List<Descriptors.FileDescriptor> fds = buildFileDescriptors(set);
            ProtobufDescriptorRegistry reg = new ProtobufDescriptorRegistry();
            for (Descriptors.FileDescriptor fd : fds) {
                for (Descriptors.Descriptor md : fd.getMessageTypes()) {
                    registerMessages(md, reg.typesByFullName);
                }
            }
            return reg;
        } catch (Descriptors.DescriptorValidationException e) {
            throw new IOException("descriptor validation failed", e);
        }
    }

    /** Builds a registry from already-validated file descriptors (unit tests). */
    public static ProtobufDescriptorRegistry fromFileDescriptors(Descriptors.FileDescriptor... fds) {
        ProtobufDescriptorRegistry reg = new ProtobufDescriptorRegistry();
        for (Descriptors.FileDescriptor fd : fds) {
            for (Descriptors.Descriptor md : fd.getMessageTypes()) {
                registerMessages(md, reg.typesByFullName);
            }
        }
        return reg;
    }

    public Descriptors.Descriptor find(String fullMessageName) {
        return typesByFullName.get(fullMessageName);
    }

    private static void registerMessages(Descriptors.Descriptor d, Map<String, Descriptors.Descriptor> out) {
        out.put(d.getFullName(), d);
        for (Descriptors.Descriptor nested : d.getNestedTypes()) {
            registerMessages(nested, out);
        }
    }

    private static List<Descriptors.FileDescriptor> buildFileDescriptors(DescriptorProtos.FileDescriptorSet set)
            throws Descriptors.DescriptorValidationException {
        Map<String, DescriptorProtos.FileDescriptorProto> protosByName = new LinkedHashMap<>();
        for (DescriptorProtos.FileDescriptorProto fp : set.getFileList()) {
            protosByName.put(fp.getName(), fp);
        }
        Map<String, Descriptors.FileDescriptor> cache = new HashMap<>();
        List<Descriptors.FileDescriptor> ordered = new ArrayList<>();
        for (DescriptorProtos.FileDescriptorProto fp : set.getFileList()) {
            ordered.add(resolve(fp.getName(), protosByName, cache));
        }
        return ordered;
    }

    private static Descriptors.FileDescriptor resolve(
            String name,
            Map<String, DescriptorProtos.FileDescriptorProto> protosByName,
            Map<String, Descriptors.FileDescriptor> cache) throws Descriptors.DescriptorValidationException {
        if (cache.containsKey(name)) {
            return cache.get(name);
        }
        DescriptorProtos.FileDescriptorProto fp = protosByName.get(name);
        Descriptors.FileDescriptor[] deps = new Descriptors.FileDescriptor[fp.getDependencyCount()];
        for (int i = 0; i < deps.length; i++) {
            deps[i] = resolve(fp.getDependency(i), protosByName, cache);
        }
        Descriptors.FileDescriptor fd = Descriptors.FileDescriptor.buildFrom(fp, deps);
        cache.put(name, fd);
        return fd;
    }
}
