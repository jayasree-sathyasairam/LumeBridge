package com.lumebridge.plugin;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricsExporterPluginTest {

    @Test
    void collectsAllMetrics() {
        MetricsExporterPlugin exporter = new MetricsExporterPlugin();
        Map<String, Object> all = exporter.collectAll();
        
        assertNotNull(all);
        assertTrue(all.containsKey("locks"));
    }
}
