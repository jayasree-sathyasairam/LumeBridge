package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.db.TaskRecord;
import com.lumebridge.db.TaskRepository;
import com.lumebridge.pipeline.RequestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CollisionDetectionPluginTest {

    @Mock
    TaskRepository tasks;

    @Test
    void disabledPassesThrough() throws Exception {
        CollisionDetectionPlugin c = new CollisionDetectionPlugin(tasks);
        c.init(Map.of(SentinelConstants.CFG_KEY_MODE, SentinelConstants.COLLISION_MODE_DISABLED));
        RequestContext ctx = new RequestContext("{\"result\":1}".getBytes());
        ctx.setPayloadHash("h");
        c.middleware().apply(ctx, () -> { });
        verifyNoInteractions(tasks);
    }

    @Test
    void manualModeConflictOnHashMismatch() throws Exception {
        CollisionDetectionPlugin c = new CollisionDetectionPlugin(tasks);
        c.init(Map.of(SentinelConstants.CFG_KEY_MODE, SentinelConstants.COLLISION_MODE_MANUAL));
        when(tasks.findByPayloadHash("h")).thenReturn(Optional.of(new TaskRecord(1, "old-hash", "{\"x\":1}")));
        RequestContext ctx = new RequestContext("{\"result\":2}".getBytes());
        ctx.setPayloadHash("h");
        c.middleware().apply(ctx, () -> {
            throw new AssertionError("next must not run");
        });
        assertEquals(SentinelConstants.HTTP_STATUS_CONFLICT, ctx.getHttpStatus());
        assertEquals(SentinelConstants.STATUS_COLLISION, ctx.getStatus());
    }

    @Test
    void autoReprocessOverwrites() throws Exception {
        CollisionDetectionPlugin c = new CollisionDetectionPlugin(tasks);
        c.init(Map.of(SentinelConstants.CFG_KEY_MODE, SentinelConstants.COLLISION_MODE_AUTO_REPROCESS));
        when(tasks.findByPayloadHash("h")).thenReturn(Optional.of(new TaskRecord(1, "old", "{\"x\":1}")));
        RequestContext ctx = new RequestContext("{\"result\":9}".getBytes());
        ctx.setPayloadHash("h");
        c.middleware().apply(ctx, () -> { });
        ArgumentCaptor<String> res = ArgumentCaptor.forClass(String.class);
        verify(tasks).overwriteResult(eq("h"), res.capture(), anyString());
        assertEquals("9", res.getValue());
    }
}
