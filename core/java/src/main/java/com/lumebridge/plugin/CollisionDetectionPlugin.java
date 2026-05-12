package com.lumebridge.plugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.lumebridge.SentinelConstants;
import com.lumebridge.db.TaskRepository;
import com.lumebridge.pipeline.MiddlewareFunc;
import com.lumebridge.pipeline.Plugin;
import com.lumebridge.pipeline.RequestContext;
import com.lumebridge.pipeline.Stage;
import com.lumebridge.util.Hashing;
import com.lumebridge.util.JsonBody;

import java.util.Map;
import java.util.Optional;

/**
 * Detects same payload_hash with different result fingerprint (response_hash).
 * Delegates collision handling to pluggable strategies to reduce cyclomatic complexity.
 */
public class CollisionDetectionPlugin implements Plugin {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final TaskRepository tasks;
    private CollisionHandler handler;

    public CollisionDetectionPlugin(TaskRepository tasks) {
        this.tasks = tasks;
    }

    @Override public String name() { return SentinelConstants.PLUGIN_COLLISION_DETECTION; }
    @Override public Stage stage() { return Stage.DEDUP; }
    @Override public int order() { return 5; }

    @Override
    public void init(Map<String, String> config) {
        String mode = config.getOrDefault(SentinelConstants.CFG_KEY_MODE, SentinelConstants.COLLISION_MODE_DISABLED).toLowerCase();
        this.handler = createHandler(mode);
    }

    private CollisionHandler createHandler(String mode) {
        if (SentinelConstants.COLLISION_MODE_MANUAL.equals(mode)) {
            return new ManualHandler();
        }
        if (SentinelConstants.COLLISION_MODE_AUTO_REPROCESS.equals(mode)) {
            return new AutoReprocessHandler(tasks);
        }
        return new DisabledHandler();
    }

    @Override
    public MiddlewareFunc middleware() {
        return (ctx, next) -> {
            if (isDisabledHandler(handler)) {
                next.run();
                return;
            }

            String hash = ctx.getPayloadHash();
            String incomingResult = extractResultValue(ctx);
            String incomingHash = extractResponseHash(ctx);
            Optional<?> stored = tasks.findByPayloadHash(hash);

            if (isNoStoredHash(stored)) {
                next.run();
                return;
            }

            String storedHash = ((com.lumebridge.db.TaskRecord) stored.get()).responseHash();
            if (storedHash.equals(incomingHash)) {
                next.run();
                return;
            }

            handler.handleCollision(ctx, hash, storedHash, incomingHash, incomingResult);
            if (isManualHandler(handler)) {
                return;
            }
            next.run();
        };
    }

    private boolean isDisabledHandler(CollisionHandler handler) {
        return handler instanceof DisabledHandler;
    }

    private boolean isManualHandler(CollisionHandler handler) {
        return handler instanceof ManualHandler;
    }

    private boolean isNoStoredHash(Optional<?> stored) {
        if (stored.isEmpty()) {
            return true;
        }
        var record = ((com.lumebridge.db.TaskRecord) stored.get());
        return record.responseHash() == null;
    }

    private String extractResultValue(RequestContext ctx) {
        var body = JsonBody.tryParse(ctx.getRawPayload());
        if (!isValidBodyWithResult(body)) {
            return "";
        }
        var result = body.getAsJsonObject().get(SentinelConstants.JSON_FIELD_RESULT);
        if (result.isJsonNull()) {
            return "";
        }
        return GSON.toJson(result);
    }

    private String extractResponseHash(RequestContext ctx) {
        var body = JsonBody.tryParse(ctx.getRawPayload());
        if (!isValidBodyWithResult(body)) {
            return "";
        }
        var result = body.getAsJsonObject().get(SentinelConstants.JSON_FIELD_RESULT);
        if (result.isJsonNull()) {
            return "";
        }
        String resultJson = GSON.toJson(result);
        return Hashing.sha256Hex(resultJson);
    }

    private boolean isValidBodyWithResult(com.google.gson.JsonElement body) {
        return body != null
               && body.isJsonObject()
               && body.getAsJsonObject().has(SentinelConstants.JSON_FIELD_RESULT);
    }

    interface CollisionHandler {
        void handleCollision(RequestContext ctx, String hash, String stored, String incoming, String result);
    }

    static class DisabledHandler implements CollisionHandler {
        @Override
        public void handleCollision(RequestContext ctx, String hash, String stored, String incoming, String result) {
            // No-op: disabled mode allows collision to pass through
        }
    }

    static class ManualHandler implements CollisionHandler {
        @Override
        public void handleCollision(RequestContext ctx, String hash, String stored, String incoming, String result) {
            ctx.setHttpStatus(SentinelConstants.HTTP_STATUS_CONFLICT);
            ctx.setStatus(SentinelConstants.STATUS_COLLISION);
            ctx.getMetadata().put(SentinelConstants.META_STORED_RESPONSE_HASH, stored);
            ctx.getMetadata().put(SentinelConstants.META_INCOMING_RESPONSE_HASH, incoming);
        }
    }

    static class AutoReprocessHandler implements CollisionHandler {
        private final TaskRepository tasks;

        AutoReprocessHandler(TaskRepository tasks) {
            this.tasks = tasks;
        }

        @Override
        public void handleCollision(RequestContext ctx, String hash, String stored, String incoming, String result) {
            try {
                tasks.overwriteResult(hash, result, incoming);
                ctx.getMetadata().put(SentinelConstants.META_PERSIST_DONE, SentinelConstants.VAL_TRUE);
            } catch (java.sql.SQLException e) {
                ctx.getMetadata().put(SentinelConstants.META_PERSIST_DONE, SentinelConstants.VAL_FALSE);
            }
        }
    }
}
