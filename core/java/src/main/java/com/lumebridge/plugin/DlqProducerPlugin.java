package com.lumebridge.plugin;

import com.google.gson.Gson;
import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.MiddlewareFunc;
import com.lumebridge.pipeline.Plugin;
import com.lumebridge.pipeline.RequestContext;
import com.lumebridge.pipeline.Stage;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * After the request finishes, publishes a compact record to the DLQ topic for hard failures
 * ({@code httpStatus >= 500}, bad request with error, or {@link SentinelConstants#META_DLQ} in metadata).
 */
public class DlqProducerPlugin implements Plugin {

    private static final Gson GSON = new Gson();

    private KafkaProducer<byte[], byte[]> producer;
    private String topic = SentinelConstants.DEFAULT_DLQ_TOPIC;
    private int maxRecordBytes = SentinelConstants.DEFAULT_MAX_RECORD_BYTES;

    @Override public String name() { return SentinelConstants.PLUGIN_DLQ; }
    @Override public Stage stage() { return Stage.POST_PROCESS; }
    @Override public int order() { return 1; }

    @Override
    public void init(Map<String, String> config) {
        this.topic = config.getOrDefault(SentinelConstants.CORE_KEY_DLQ_TOPIC, SentinelConstants.DEFAULT_DLQ_TOPIC);
        this.maxRecordBytes = Integer.parseInt(config.getOrDefault(
                SentinelConstants.CFG_KEY_MAX_RECORD_BYTES,
                Integer.toString(SentinelConstants.DEFAULT_MAX_RECORD_BYTES)));

        String bootstrap = config.getOrDefault(
                SentinelConstants.CORE_KEY_KAFKA_BOOTSTRAP_SERVERS,
                SentinelConstants.DEFAULT_KAFKA_BOOTSTRAP_SERVERS);

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, SentinelConstants.KAFKA_ACKS_ALL);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, SentinelConstants.KAFKA_ENABLE_IDEMPOTENCE_FALSE);
        props.put(ProducerConfig.CLIENT_ID_CONFIG, SentinelConstants.KAFKA_CLIENT_ID_DLQ);

        try {
            this.producer = new KafkaProducer<>(props);
            System.out.println("DLQ producer ready: topic=" + topic + " bootstrap=" + bootstrap);
        } catch (Exception e) {
            System.err.println("DLQ producer disabled (init failed): " + e.getMessage());
            this.producer = null;
        }
    }

    @Override
    public MiddlewareFunc middleware() {
        return (ctx, next) -> {
            next.run();
            if (producer == null || SentinelConstants.VAL_TRUE.equalsIgnoreCase(ctx.getMetadata().get(SentinelConstants.META_DLQ_SKIP))) {
                return;
            }
            if (!shouldSend(ctx)) {
                return;
            }
            byte[] key = ctx.getPayloadHash() != null
                    ? ctx.getPayloadHash().getBytes(StandardCharsets.UTF_8)
                    : ctx.getRequestId() != null
                            ? ctx.getRequestId().getBytes(StandardCharsets.UTF_8)
                            : new byte[0];
            byte[] value = buildRecordJson(ctx);
            if (value.length > maxRecordBytes) {
                byte[] capped = new byte[maxRecordBytes];
                System.arraycopy(value, 0, capped, 0, maxRecordBytes);
                value = capped;
            }
            try {
                producer.send(new ProducerRecord<>(topic, key, value));
            } catch (Exception e) {
                System.err.println("DLQ send failed: " + e.getMessage());
            }
        };
    }

    private static boolean shouldSend(RequestContext ctx) {
        if (SentinelConstants.VAL_TRUE.equalsIgnoreCase(ctx.getMetadata().get(SentinelConstants.META_DLQ))) {
            return true;
        }
        int s = ctx.getHttpStatus();
        if (s >= SentinelConstants.HTTP_STATUS_INTERNAL_ERROR) {
            return true;
        }
        return s == SentinelConstants.HTTP_STATUS_BAD_REQUEST && SentinelConstants.STATUS_ERROR.equals(ctx.getStatus());
    }

    private byte[] buildRecordJson(RequestContext ctx) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(SentinelConstants.DLQ_JSON_PAYLOAD_HASH, ctx.getPayloadHash());
        m.put(SentinelConstants.DLQ_JSON_REQUEST_ID, ctx.getRequestId());
        m.put(SentinelConstants.DLQ_JSON_HTTP_STATUS, ctx.getHttpStatus());
        m.put(SentinelConstants.DLQ_JSON_STATUS, ctx.getStatus());
        if (ctx.getError() != null) {
            m.put(SentinelConstants.DLQ_JSON_ERROR, ctx.getError().getMessage());
        }
        if (ctx.getRawPayload() != null) {
            String raw = new String(ctx.getRawPayload(), StandardCharsets.UTF_8);
            int maxPayload = SentinelConstants.DEFAULT_DLQ_PAYLOAD_PREVIEW_MAX_CHARS;
            if (raw.length() > maxPayload) {
                raw = raw.substring(0, maxPayload) + SentinelConstants.TRUNCATION_SUFFIX;
            }
            m.put(SentinelConstants.DLQ_JSON_PAYLOAD_PREVIEW, raw);
        }
        return GSON.toJson(m).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        if (producer != null) {
            producer.close();
        }
    }
}
