# ADR-006: Multi-Format Payload Canonicalization (MessagePack & Protobuf)

## Status

Accepted

## Context

`payload-hasher` and downstream deduplication depend on **stable raw payload bytes** before hashing. Requests arrive as JSON, XML, YAML, `application/x-www-form-urlencoded`, **MessagePack**, **Protobuf**, or opaque binary (`application/octet-stream`). Equivalent logical payloads can differ on the wire (map key order, insignificant whitespace, protobuf unknown-field ordering, etc.). Earlier revisions treated **protobuf** and **MessagePack** as opaque binary with only warnings—fine for traceability but weak for **cross-instance dedup** when clients encode the same intent differently.

## Decision

Extend **`payload-normalizer`** (`PayloadNormalizerPlugin` → `MultiFormatPayloadNormalizerEngine`) so that:

1. **MessagePack** (`application/msgpack` or sniff heuristic): Decode to a **single root MessagePack value**, reject payloads with **trailing bytes** after that root, map scalars/maps/arrays to a structured tree, convert **binary** fields to deterministic **`hex:`…** literals, convert values not modeled as plain scalars/maps/arrays (e.g. extensions) by **re-encoding the value with MessagePack and hashing that wiring as `hex:`…**, then emit **sorted-key JSON UTF-8** via the shared structured converter used by YAML/MessagePack trees.

2. **Protobuf** (Content-Type containing `protobuf` / `x-protobuf`): **Canonicalize only when** the deployment loads a **`FileDescriptorSet`** from **`plugins.payload-normalizer.protobuf_descriptor_path`** and the client sends the fully-qualified message name in **`X-Protobuf-Message`** (configurable via **`protobuf_message_header`**, default `X-Protobuf-Message`). Use **`DynamicMessage.parseFrom`** with the registry-backed **`Descriptor`**, print JSON with **`JsonFormat`** (`preservingProtoFieldNames`, **`includingDefaultValueFields`**), then run the existing **JSON canonical UTF-8** step so nested keys stay sorted for hashing.

3. If protobuf canonicalization **cannot** run (missing descriptor file, missing/blank header, parse failure): **leave the raw body unchanged**, add an explicit **warning** describing the requirement (`protobuf_descriptor_path` + header), and hash as-is—same operational posture as before, but with clearer semantics than generic “opaque/binary.”

4. **`application/octet-stream`** remains **`BINARY_OPAQUE`**: warn and **do not** reinterpret bytes.

5. **Detection order**: Prefer **`Content-Type`** primary MIME; when unknown, **sniff** (JSON/XML/YAML/form/MessagePack heuristics) before treating payload as unchanged.

## Rationale

- **MessagePack** has no universal schema on the wire; canonicalization is defined as **sorted JSON over a deterministic structured projection**. Binary and extension payloads stay deterministic without pretending they are human-readable objects.

- **Protobuf** cannot be interpreted without **descriptors**. Shipping a **`FileDescriptorSet`** (produced at build time from `.proto` files) keeps parsing **self-contained** at the gateway—no runtime dependency on a reflection API or external registry.

- **Header-based message type** matches typical gateways and avoids guessing one message type per endpoint when multiple RPC shapes share a route.

- Aligning protobuf output with **sorted JSON** reuses **`JsonCanonicalNormalizer`** and keeps **one** downstream canonical representation for audit/compare tooling.

## Consequences

- Operators who want **protobuf-aware dedup** must **publish a descriptor set file** and ensure clients send **`X-Protobuf-Message`** (or the configured header name).

- MessagePack clients must send **one root value** per body; **concatenated** MessagePack values are rejected during normalization (warning + unchanged body path via exception handling in the engine).

- Hash stability for protobuf assumes **descriptor compatibility** with the wire payload (same field numbers and wire types). Breaking `.proto` changes require regenerating and redeploying the descriptor set.

- Adding MessagePack/Protobuf libraries increases **classpath surface**; acceptable for core gateway behavior locked behind explicit Content-Type/sniff rules.

## Alternatives Considered

- **Always treat protobuf/MessagePack as opaque**: Rejected for V2 P0 where stable hashing across equivalent encodings was explicitly required.

- **Protobuf reflection / runtime schema registry**: Deferred—adds operational coupling and auth/network concerns; **`FileDescriptorSet` on disk** is simpler for controlled deployments.

- **Infer protobuf message type without a header**: Rejected—ambiguous when multiple messages share similar prefixes or evolve; explicit header is cheaper than wrong parses.
