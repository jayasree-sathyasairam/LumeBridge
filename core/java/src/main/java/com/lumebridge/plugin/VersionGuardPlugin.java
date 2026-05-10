package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.db.TaskRepository;
import com.lumebridge.pipeline.MiddlewareFunc;
import com.lumebridge.pipeline.Plugin;
import com.lumebridge.pipeline.Stage;
import com.lumebridge.util.JsonBody;

/**
 * Optimistic concurrency: if the client sends {@code expected_version}, it must match the DB row version.
 */
public class VersionGuardPlugin implements Plugin {

    private final TaskRepository tasks;

    public VersionGuardPlugin(TaskRepository tasks) {
        this.tasks = tasks;
    }

    @Override public String name() { return SentinelConstants.PLUGIN_VERSION_GUARD; }
    @Override public Stage stage() { return Stage.DEDUP; }
    @Override public int order() { return 3; }

    @Override
    public MiddlewareFunc middleware() {
        return (ctx, next) -> {
            String hash = ctx.getPayloadHash();
            var body = JsonBody.tryParse(ctx.getRawPayload());
            if (body == null || !body.has(SentinelConstants.JSON_FIELD_EXPECTED_VERSION)) {
                next.run();
                return;
            }
            int expected = body.get(SentinelConstants.JSON_FIELD_EXPECTED_VERSION).getAsInt();
            var row = tasks.findByPayloadHash(hash);
            if (row.isEmpty()) {
                next.run();
                return;
            }
            if (expected != row.get().version()) {
                ctx.setHttpStatus(SentinelConstants.HTTP_STATUS_CONFLICT);
                ctx.setStatus(SentinelConstants.STATUS_STALE_VERSION);
                ctx.getMetadata().put(SentinelConstants.META_DB_VERSION, String.valueOf(row.get().version()));
                ctx.getMetadata().put(SentinelConstants.JSON_FIELD_EXPECTED_VERSION, String.valueOf(expected));
                return;
            }
            next.run();
        };
    }
}
