package com.lumebridge.payload;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.lumebridge.SentinelConstants;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.nio.charset.StandardCharsets;

/** Loads YAML safely, converts to sorted JSON UTF-8 bytes for stable hashing. */
public final class YamlCanonicalNormalizer {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private YamlCanonicalNormalizer() {}

    public static byte[] normalizeUtf8(byte[] raw) {
        LoaderOptions lo = new LoaderOptions();
        lo.setCodePointLimit(Math.min(SentinelConstants.MAX_PAYLOAD_SIZE * 8, 2_000_000));
        Yaml yaml = new Yaml(new SafeConstructor(lo));
        Object root = yaml.load(new String(raw, StandardCharsets.UTF_8));
        JsonElement je = StructuredPayloadConverter.toJsonElement(root);
        return GSON.toJson(je).getBytes(StandardCharsets.UTF_8);
    }
}
