package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.db.TaskRecord;
import com.lumebridge.db.TaskRepository;
import com.lumebridge.pipeline.RequestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VersionGuardPluginTest {

    @Mock
    TaskRepository tasks;

    @Test
    void skipsWhenExpectedVersionAbsent() throws Exception {
        VersionGuardPlugin v = new VersionGuardPlugin(tasks);
        RequestContext ctx = new RequestContext("{\"a\":1}".getBytes());
        ctx.setPayloadHash("h");
        v.middleware().apply(ctx, () -> { });
        verify(tasks, never()).findByPayloadHash(anyString());
        assertEquals(SentinelConstants.HTTP_STATUS_OK, ctx.getHttpStatus());
    }

    @Test
    void allowsWhenRowMissing() throws Exception {
        VersionGuardPlugin v = new VersionGuardPlugin(tasks);
        when(tasks.findByPayloadHash("h")).thenReturn(Optional.empty());
        RequestContext ctx = new RequestContext("{\"expected_version\":1}".getBytes());
        ctx.setPayloadHash("h");
        v.middleware().apply(ctx, () -> { });
        assertEquals(SentinelConstants.HTTP_STATUS_OK, ctx.getHttpStatus());
    }

    @Test
    void conflictWhenVersionMismatch() throws Exception {
        VersionGuardPlugin v = new VersionGuardPlugin(tasks);
        when(tasks.findByPayloadHash("h")).thenReturn(Optional.of(new TaskRecord(5, "r", "{}")));
        RequestContext ctx = new RequestContext("{\"expected_version\":1}".getBytes());
        ctx.setPayloadHash("h");
        v.middleware().apply(ctx, () -> {
            throw new AssertionError("should not run next");
        });
        assertEquals(SentinelConstants.HTTP_STATUS_CONFLICT, ctx.getHttpStatus());
        assertEquals(SentinelConstants.STATUS_STALE_VERSION, ctx.getStatus());
    }
}
