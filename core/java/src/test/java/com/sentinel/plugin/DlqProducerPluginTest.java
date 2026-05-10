package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.RequestContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DlqProducerPluginTest {

    @Test
    void sendsToKafkaOnError() throws Exception {
        DlqProducerPlugin plugin = new DlqProducerPlugin();
        org.apache.kafka.clients.producer.KafkaProducer<byte[], byte[]> mockProducer = mock(org.apache.kafka.clients.producer.KafkaProducer.class);
        
        try {
            java.lang.reflect.Field field = DlqProducerPlugin.class.getDeclaredField("producer");
            field.setAccessible(true);
            field.set(plugin, mockProducer);
        } catch (Exception e) {
            fail("Failed to inject mock producer");
        }

        RequestContext ctx = new RequestContext("{}".getBytes());
        ctx.setHttpStatus(500);
        ctx.setStatus(SentinelConstants.STATUS_ERROR);
        ctx.setError(new RuntimeException("Crash"));

        plugin.middleware().apply(ctx, () -> {});

        verify(mockProducer).send(any());
    }

    @Test
    void skipsOnSuccess() throws Exception {
        DlqProducerPlugin plugin = new DlqProducerPlugin();
        org.apache.kafka.clients.producer.KafkaProducer<byte[], byte[]> mockProducer = mock(org.apache.kafka.clients.producer.KafkaProducer.class);
        
        try {
            java.lang.reflect.Field field = DlqProducerPlugin.class.getDeclaredField("producer");
            field.setAccessible(true);
            field.set(plugin, mockProducer);
        } catch (Exception e) {
            fail("Failed to inject mock producer");
        }

        RequestContext ctx = new RequestContext("{}".getBytes());
        ctx.setHttpStatus(200);

        plugin.middleware().apply(ctx, () -> {});

        verify(mockProducer, never()).send(any());
    }
}
