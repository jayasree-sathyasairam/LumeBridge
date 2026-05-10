package com.lumebridge.config;

import com.lumebridge.SentinelConstants;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads configuration from lumebridge.yaml with environment variable overrides.
 *
 * Resolution order (highest priority first):
 *   1. Environment variables  (e.g. THROTTLE_PERMITS=20)
 *   2. YAML config file       (e.g. plugins.concurrency-gate.permits: 20)
 *   3. Hardcoded defaults
 */
public class ConfigLoader {

    private final Map<String, String> core = new LinkedHashMap<>();
    private final Map<String, Map<String, String>> plugins = new LinkedHashMap<>();

    public static ConfigLoader load() {
        String configPath = env("CONFIG_FILE", "lumebridge.yaml");
        return fromPath(Path.of(configPath));
    }

    /**
     * Loads the same YAML/env/profile rules as {@link #load()}, but from an explicit path (for tests and tooling).
     */
    public static ConfigLoader fromPath(Path path) {
        ConfigLoader loader = new ConfigLoader();
        if (Files.exists(path)) {
            loader.loadYaml(path);
            System.out.println("Config loaded from: " + path.toAbsolutePath());
        } else {
            System.out.println("No config file found at " + path + ", using defaults + env vars");
        }
        loader.applyEnvOverrides();
        loader.applyProfile();
        return loader;
    }

    @SuppressWarnings("unchecked")
    private void loadYaml(Path path) {
        try (InputStream in = new FileInputStream(path.toFile())) {
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(in);
            if (root == null) return;

            if (root.containsKey(SentinelConstants.YAML_ROOT_CORE)) {
                Map<String, Object> coreMap = (Map<String, Object>) root.get(SentinelConstants.YAML_ROOT_CORE);
                if (coreMap != null) {
                    coreMap.forEach((k, v) -> core.put(k, String.valueOf(v)));
                }
            }

            if (root.containsKey(SentinelConstants.YAML_ROOT_PLUGINS)) {
                Map<String, Object> pluginsMap = (Map<String, Object>) root.get(SentinelConstants.YAML_ROOT_PLUGINS);
                if (pluginsMap != null) {
                    pluginsMap.forEach((pluginName, pluginConfig) -> {
                        Map<String, String> cfg = new LinkedHashMap<>();
                        if (pluginConfig instanceof Map<?, ?> m) {
                            m.forEach((k, v) -> cfg.put(String.valueOf(k), String.valueOf(v)));
                        }
                        plugins.put(pluginName, cfg);
                    });
                }
            }

            mergeOptionalRootSections(root);
        } catch (IOException e) {
            System.err.println("Warning: could not read config file: " + e.getMessage());
        }
    }

    private void applyEnvOverrides() {
        override("PORT", SentinelConstants.CORE_KEY_PORT);
        override("THROTTLE_PERMITS", SentinelConstants.CORE_KEY_PERMITS);
        override("WORK_DELAY_MS", SentinelConstants.CORE_KEY_DELAY_MS);
        override("LOCK_TTL_SECONDS", SentinelConstants.CORE_KEY_LOCK_TTL_SECONDS);
        override("REDIS_HOST", SentinelConstants.CORE_KEY_REDIS_HOST);
        override("REDIS_PORT", SentinelConstants.CORE_KEY_REDIS_PORT);
        override("POSTGRES_HOST", SentinelConstants.CORE_KEY_POSTGRES_HOST);
        override("POSTGRES_PORT", SentinelConstants.CORE_KEY_POSTGRES_PORT);
        override("POSTGRES_USER", SentinelConstants.CORE_KEY_POSTGRES_USER);
        override("POSTGRES_PASSWORD", SentinelConstants.CORE_KEY_POSTGRES_PASSWORD);
        override("POSTGRES_DB", SentinelConstants.CORE_KEY_POSTGRES_DB);
        override("JDBC_URL", SentinelConstants.CORE_KEY_JDBC_URL);
        override("KAFKA_BOOTSTRAP_SERVERS", SentinelConstants.CORE_KEY_KAFKA_BOOTSTRAP_SERVERS);
        override("DLQ_TOPIC", SentinelConstants.CORE_KEY_DLQ_TOPIC);
    }

    /**
     * Optional nested blocks (same file as {@code core:}) — values override flat {@code core} keys.
     */
    @SuppressWarnings("unchecked")
    private void mergeOptionalRootSections(Map<String, Object> root) {
        if (root.containsKey(SentinelConstants.YAML_ROOT_SERVER) && root.get(SentinelConstants.YAML_ROOT_SERVER) instanceof Map<?, ?> m) {
            putIfString(m, SentinelConstants.YAML_NEST_PORT, SentinelConstants.CORE_KEY_PORT);
        }
        if (root.containsKey(SentinelConstants.YAML_ROOT_REDIS) && root.get(SentinelConstants.YAML_ROOT_REDIS) instanceof Map<?, ?> m) {
            putIfString(m, SentinelConstants.YAML_NEST_HOST, SentinelConstants.CORE_KEY_REDIS_HOST);
            putIfString(m, SentinelConstants.YAML_NEST_PORT, SentinelConstants.CORE_KEY_REDIS_PORT);
        }
        if (root.containsKey(SentinelConstants.YAML_ROOT_POSTGRES) && root.get(SentinelConstants.YAML_ROOT_POSTGRES) instanceof Map<?, ?> m) {
            putIfString(m, SentinelConstants.YAML_NEST_HOST, SentinelConstants.CORE_KEY_POSTGRES_HOST);
            putIfString(m, SentinelConstants.YAML_NEST_PORT, SentinelConstants.CORE_KEY_POSTGRES_PORT);
            putIfString(m, SentinelConstants.YAML_NEST_USER, SentinelConstants.CORE_KEY_POSTGRES_USER);
            putIfString(m, SentinelConstants.YAML_NEST_PASSWORD, SentinelConstants.CORE_KEY_POSTGRES_PASSWORD);
            putIfString(m, SentinelConstants.YAML_NEST_DATABASE, SentinelConstants.CORE_KEY_POSTGRES_DB);
        }
        if (root.containsKey(SentinelConstants.YAML_ROOT_KAFKA) && root.get(SentinelConstants.YAML_ROOT_KAFKA) instanceof Map<?, ?> m) {
            putIfString(m, SentinelConstants.YAML_NEST_BOOTSTRAP_SERVERS, SentinelConstants.CORE_KEY_KAFKA_BOOTSTRAP_SERVERS);
            putIfString(m, SentinelConstants.YAML_NEST_DLQ_TOPIC, SentinelConstants.CORE_KEY_DLQ_TOPIC);
        }
    }

    private void putIfString(Map<?, ?> m, String yamlKey, String coreKey) {
        if (m.containsKey(yamlKey)) {
            Object v = m.get(yamlKey);
            if (v != null) {
                core.put(coreKey, String.valueOf(v));
            }
        }
    }

    private void override(String envKey, String configKey) {
        String val = System.getenv(envKey);
        if (val != null) {
            core.put(configKey, val);
        }
    }

    private void applyProfile() {
        String profile = env("PROFILE", core.getOrDefault(SentinelConstants.CORE_KEY_PROFILE, ""));
        if (profile.isEmpty()) return;

        System.out.println("Applying profile: " + profile);
        switch (profile) {
            case SentinelConstants.PROFILE_LIGHTWEIGHT -> {
                enablePlugin(SentinelConstants.PLUGIN_SMART_RETRY);
                enablePlugin(SentinelConstants.PLUGIN_CIRCUIT_BREAKER);
                enablePlugin(SentinelConstants.PLUGIN_LOCK_METRICS);
                enablePlugin(SentinelConstants.PLUGIN_STALE_DATA_CLEANER);
                enablePlugin(SentinelConstants.PLUGIN_METRICS_EXPORTER);
            }
            case SentinelConstants.PROFILE_STANDARD -> {
                enablePlugin(SentinelConstants.PLUGIN_COLLISION_DETECTION);
                enablePlugin(SentinelConstants.PLUGIN_SMART_RETRY);
                enablePlugin(SentinelConstants.PLUGIN_CIRCUIT_BREAKER);
                enablePlugin(SentinelConstants.PLUGIN_DLQ);
                enablePlugin(SentinelConstants.PLUGIN_LOCK_METRICS);
                enablePlugin(SentinelConstants.PLUGIN_STALE_DATA_CLEANER);
                enablePlugin(SentinelConstants.PLUGIN_METRICS_EXPORTER);
                enablePlugin(SentinelConstants.PLUGIN_TELEMETRY);
                enablePlugin(SentinelConstants.PLUGIN_PAYLOAD_NORMALIZER);
            }
            case SentinelConstants.PROFILE_AI -> {
                enablePlugin(SentinelConstants.PLUGIN_SEMANTIC_CACHE);
                enablePlugin(SentinelConstants.PLUGIN_PII_SCRUBBER);
                enablePlugin(SentinelConstants.PLUGIN_SMART_RETRY);
                enablePlugin(SentinelConstants.PLUGIN_CIRCUIT_BREAKER);
                enablePlugin(SentinelConstants.PLUGIN_DLQ);
                enablePlugin(SentinelConstants.PLUGIN_STALE_DATA_CLEANER);
                enablePlugin(SentinelConstants.PLUGIN_METRICS_EXPORTER);
                enablePlugin(SentinelConstants.PLUGIN_TELEMETRY);
            }
            case SentinelConstants.PROFILE_API_MINIMAL -> {
                enablePlugin(SentinelConstants.PLUGIN_API_KEY_AUTH);
                enablePlugin(SentinelConstants.PLUGIN_CONCURRENCY_GATE);
                enablePlugin(SentinelConstants.PLUGIN_PAYLOAD_HASHER);
                enablePlugin(SentinelConstants.PLUGIN_DISTRIBUTED_LOCK);
                enablePlugin(SentinelConstants.PLUGIN_TASK_PERSISTENCE);
                enablePlugin(SentinelConstants.PLUGIN_DUAL_MODE_ROUTER);
            }
            case SentinelConstants.PROFILE_AI_MINIMAL -> {
                enablePlugin(SentinelConstants.PROFILE_API_MINIMAL); // Inheritance
                enablePlugin(SentinelConstants.PLUGIN_INTELLIGENT_ROUTER);
            }
            case SentinelConstants.PROFILE_API_PRO -> {
                enablePlugin(SentinelConstants.PROFILE_API_MINIMAL);
                enablePlugin(SentinelConstants.PLUGIN_CLIENT_QUOTA);
                enablePlugin(SentinelConstants.PLUGIN_PAYLOAD_NORMALIZER);
                enablePlugin(SentinelConstants.PLUGIN_NONCE_ORDERING);
                enablePlugin(SentinelConstants.PLUGIN_COLLISION_DETECTION);
                enablePlugin(SentinelConstants.PLUGIN_SMART_RETRY);
                enablePlugin(SentinelConstants.PLUGIN_CIRCUIT_BREAKER);
                enablePlugin(SentinelConstants.PLUGIN_METRICS_EXPORTER);
                enablePlugin(SentinelConstants.PLUGIN_STALE_DATA_CLEANER);
                enablePlugin(SentinelConstants.PLUGIN_LOCK_METRICS);
            }
            case SentinelConstants.PROFILE_AI_PRO -> {
                enablePlugin(SentinelConstants.PROFILE_AI_MINIMAL);
                enablePlugin(SentinelConstants.PLUGIN_INTENT_CLASSIFIER);
                enablePlugin(SentinelConstants.PLUGIN_SEMANTIC_CACHE);
                enablePlugin(SentinelConstants.PLUGIN_SAFETY_GUARDRAILS);
                enablePlugin(SentinelConstants.PLUGIN_PII_SCRUBBER);
                enablePlugin(SentinelConstants.PLUGIN_RESPONSE_EVALUATOR);
                enablePlugin(SentinelConstants.PLUGIN_TOKEN_METER);
                // Also add reliability from Pro
                enablePlugin(SentinelConstants.PLUGIN_SMART_RETRY);
                enablePlugin(SentinelConstants.PLUGIN_CIRCUIT_BREAKER);
            }
            case SentinelConstants.PROFILE_HYBRID -> {
                enablePlugin(SentinelConstants.PROFILE_API_PRO);
                enablePlugin(SentinelConstants.PROFILE_AI_PRO);
                enablePlugin(SentinelConstants.PLUGIN_DLQ);
                enablePlugin(SentinelConstants.PLUGIN_TELEMETRY);
            }
            default -> System.err.println("Unknown profile: " + profile);
        }
    }

    private void enablePlugin(String name) {
        plugins.computeIfAbsent(name, k -> new LinkedHashMap<>())
               .putIfAbsent(SentinelConstants.CFG_KEY_ENABLED, SentinelConstants.VAL_TRUE);
    }

    public Map<String, String> coreSettings() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(core));
    }

    public String coreValue(String key, String fallback) {
        return core.getOrDefault(key, fallback);
    }

    public int coreInt(String key, int fallback) {
        String val = core.get(key);
        if (val != null) {
            try { return Integer.parseInt(val); } catch (NumberFormatException ignored) {}
        }
        return fallback;
    }

    public boolean isPluginEnabled(String pluginName) {
        Map<String, String> cfg = plugins.get(pluginName);
        if (cfg == null) return false;
        return SentinelConstants.VAL_TRUE.equalsIgnoreCase(cfg.getOrDefault(SentinelConstants.CFG_KEY_ENABLED, SentinelConstants.VAL_FALSE));
    }

    public Map<String, String> pluginConfig(String pluginName) {
        return plugins.getOrDefault(pluginName, Map.of());
    }

    /**
     * Returns a merged config map: core values + plugin-specific values.
     * Plugin-specific values take precedence.
     */
    public Map<String, String> mergedConfig(String pluginName) {
        Map<String, String> merged = new LinkedHashMap<>(core);
        merged.putAll(pluginConfig(pluginName));
        return merged;
    }

    private static String env(String key, String fallback) {
        String val = System.getenv(key);
        return val != null ? val : fallback;
    }
}
