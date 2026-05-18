package com.lumebridge.config;

import com.lumebridge.SentinelConstants;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest {

    @Test
    void fromPathReadsCoreAndPlugins() throws Exception {
        Path f = Files.createTempFile("sentinel-test", ".yaml");
        Files.writeString(f, """
                core:
                  port: 9191
                plugins:
                  pii-scrubber:
                    enabled: true
                """);
        f.toFile().deleteOnExit();

        ConfigLoader cfg = ConfigLoader.fromPath(f);
        assertEquals(9191, cfg.coreInt(SentinelConstants.CORE_KEY_PORT, 0));
        assertTrue(cfg.isPluginEnabled("pii-scrubber"));
    }

    @Test
    void aiProProfileEnablesApiKeyAuth() throws Exception {
        Path f = Files.createTempFile("sentinel-ai-pro", ".yaml");
        Files.writeString(f, """
                core:
                  profile: ai-pro
                plugins: {}
                """);
        f.toFile().deleteOnExit();

        ConfigLoader cfg = ConfigLoader.fromPathForTests(f);
        assertTrue(
                cfg.isPluginEnabled(SentinelConstants.PLUGIN_API_KEY_AUTH),
                "ai-pro must include api-key-auth so stress fixtures (e.g. LB-AUTH-INVALID → 401) match the gateway");
        assertTrue(cfg.isPluginEnabled(SentinelConstants.PLUGIN_INTELLIGENT_ROUTER));
    }

    @Test
    void hybridProfileEnablesRealPluginsNotProfileAliases() throws Exception {
        Path f = Files.createTempFile("sentinel-hybrid", ".yaml");
        Files.writeString(f, """
                core:
                  profile: hybrid
                plugins: {}
                """);
        f.toFile().deleteOnExit();

        ConfigLoader cfg = ConfigLoader.fromPathForTests(f);
        assertTrue(cfg.isPluginEnabled(SentinelConstants.PLUGIN_API_KEY_AUTH));
        assertTrue(cfg.isPluginEnabled(SentinelConstants.PLUGIN_DLQ));
        assertTrue(cfg.isPluginEnabled(SentinelConstants.PLUGIN_TELEMETRY));
        assertFalse(cfg.isPluginEnabled("api-pro"));
        assertFalse(cfg.isPluginEnabled("ai-pro"));
        assertFalse(cfg.isPluginEnabled("api-minimal"));
    }

    @Test
    void aiProOverridesYamlDisabledApiKeyAuth() throws Exception {
        Path f = Files.createTempFile("sentinel-auth-disabled-yaml", ".yaml");
        Files.writeString(f, """
                core:
                  profile: ai-pro
                plugins:
                  api-key-auth:
                    enabled: false
                """);
        f.toFile().deleteOnExit();

        ConfigLoader cfg = ConfigLoader.fromPathForTests(f);
        assertTrue(cfg.isPluginEnabled(SentinelConstants.PLUGIN_API_KEY_AUTH));
    }
}
