package com.lumebridge.plugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lumebridge.SentinelConstants;
import com.lumebridge.db.TaskRepository;
import com.lumebridge.pipeline.MiddlewareFunc;
import com.lumebridge.pipeline.Plugin;
import com.lumebridge.pipeline.Stage;
import com.lumebridge.util.Hashing;
import com.lumebridge.util.JsonBody;

import java.util.Map;

/**
 * Detects same {@code payload_hash} with a different {@code result} fingerprint ({@code response_hash}).
 * Modes: {@link SentinelConstants#COLLISION_MODE_DISABLED}, {@link SentinelConstants#COLLISION_MODE_MANUAL} (409 conflict), {@link SentinelConstants#COLLISION_MODE_AUTO_REPROCESS}.
 */
public class CollisionDetectionPlugin implements Plugin {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final TaskRepository tasks;
    private String mode = SentinelConstants.COLLISION_MODE_DISABLED;

    public CollisionDetectionPlugin(TaskRepository tasks) {
        this.tasks = tasks;
    }

    @Override public String name() { return SentinelConstants.PLUGIN_COLLISION_DETECTION; }
    @Override public Stage stage() { return Stage.DEDUP; }
    @Override public int order() { return 5; }

    @Override
    public void init(Map<String, String> config) {
        this.mode = config.getOrDefault(SentinelConstants.CFG_KEY_MODE, SentinelConstants.COLLISION_MODE_DISABLED).toLowerCase();
    }

    @Override
    public MiddlewareFunc middleware() {
        return (ctx, next) -> {
            if (SentinelConstants.COLLISION_MODE_DISABLED.equals(mode)) {
                next.run();
                return;
            }
            String hash = ctx.getPayloadHash();
            var body = JsonBody.tryParse(ctx.getRawPayload());
            String incomingResultJson = null;
            if (body != null && body.has(SentinelConstants.JSON_FIELD_RESULT) && !body.get(SentinelConstants.JSON_FIELD_RESULT).isJsonNull()) {
                incomingResultJson = GSON.toJson(body.get(SentinelConstants.JSON_FIELD_RESULT));
            }
            String incomingResponseHash = incomingResultJson == null ? "" : Hashing.sha256Hex(incomingResultJson);

            var row = tasks.findByPayloadHash(hash);
            if (row.isEmpty() || row.get().responseHash() == null) {
                next.run();
                return;
            }
            String stored = row.get().responseHash();
            if (stored.equals(incomingResponseHash)) {
                next.run();
                return;
            }

            if (SentinelConstants.COLLISION_MODE_MANUAL.equals(mode)) {
                ctx.setHttpStatus(SentinelConstants.HTTP_STATUS_CONFLICT);
                ctx.setStatus(SentinelConstants.STATUS_COLLISION);
                ctx.getMetadata().put(SentinelConstants.META_STORED_RESPONSE_HASH, stored);
                ctx.getMetadata().put(SentinelConstants.META_INCOMING_RESPONSE_HASH, incomingResponseHash);
                return;
            }

            if (SentinelConstants.COLLISION_MODE_AUTO_REPROCESS.equals(mode)) {
                tasks.overwriteResult(hash, incomingResultJson, incomingResponseHash);
                ctx.getMetadata().put(SentinelConstants.META_PERSIST_DONE, SentinelConstants.VAL_TRUE);
                next.run();
                return;
            }

            next.run();
        };
    }
}
