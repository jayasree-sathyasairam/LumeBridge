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

import java.nio.charset.StandardCharsets;

/**
 * Persists payload and result to Postgres after the dedup chain succeeds.
 */
public class TaskPersistencePlugin implements Plugin {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private final TaskRepository tasks;

    public TaskPersistencePlugin(TaskRepository tasks) {
        this.tasks = tasks;
    }

    @Override public String name() { return SentinelConstants.PLUGIN_TASK_PERSISTENCE; }
    @Override public Stage stage() { return Stage.DEDUP; }
    @Override public int order() { return 6; }

    @Override
    public MiddlewareFunc middleware() {
        return (ctx, next) -> {
            next.run();

            if (SentinelConstants.VAL_TRUE.equals(ctx.getMetadata().get(SentinelConstants.META_PERSIST_DONE))) {
                return;
            }
            if (ctx.getHttpStatus() != SentinelConstants.HTTP_STATUS_OK) {
                return;
            }
            try {
                String payloadStr = new String(ctx.getRawPayload(), StandardCharsets.UTF_8);
                var body = JsonBody.tryParse(ctx.getRawPayload());
                String resultJson = null;
                if (body != null && body.has(SentinelConstants.JSON_FIELD_RESULT) && !body.get(SentinelConstants.JSON_FIELD_RESULT).isJsonNull()) {
                    resultJson = GSON.toJson(body.get(SentinelConstants.JSON_FIELD_RESULT));
                }
                String responseHash = resultJson == null ? "" : Hashing.sha256Hex(resultJson);
                tasks.upsertCompleted(ctx.getPayloadHash(), payloadStr, resultJson, responseHash);
            } catch (Exception e) {
                ctx.setHttpStatus(SentinelConstants.HTTP_STATUS_INTERNAL_ERROR);
                ctx.setStatus(SentinelConstants.STATUS_ERROR);
                ctx.setError(e instanceof Exception ex ? ex : new RuntimeException(e));
            }
        };
    }
}
