package com.lumebridge.config;

import com.lumebridge.SentinelConstants;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
