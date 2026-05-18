package com.lumebridge.payload;

/** Rough classification for canonical body normalization (V2 P0 multi-format). */
public enum PayloadMediaFamily {
    JSON,
    XML,
    FORM_URLENCODED,
    YAML,
    MSGPACK,
    /** Wire protobuf; canonical only when {@code protobuf_descriptor_path} + message header are configured. */
    PROTOBUF,
    /** Binary Content-Type without dedicated canonicalizer (e.g. raw octet-stream). */
    BINARY_OPAQUE,
    UNKNOWN
}
