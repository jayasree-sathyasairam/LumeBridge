package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.db.TaskRepository;
import com.lumebridge.pipeline.RequestContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TaskPersistencePluginTest {

    @Mock
    TaskRepository tasks;

    @Test
    void skipsWhenNotOk() throws Exception {
        TaskPersistencePlugin p = new TaskPersistencePlugin(tasks);
        RequestContext ctx = new RequestContext("{\"result\":1}".getBytes());
        ctx.setPayloadHash("h");
        ctx.setHttpStatus(500);
        p.middleware().apply(ctx, () -> { });
        verify(tasks, never()).upsertCompleted(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void upsertsOnSuccess() throws Exception {
        TaskPersistencePlugin p = new TaskPersistencePlugin(tasks);
        String json = "{\"result\":{\"x\":1}}";
        RequestContext ctx = new RequestContext(json.getBytes());
        ctx.setPayloadHash("abc");
        ctx.setHttpStatus(SentinelConstants.HTTP_STATUS_OK);
        p.middleware().apply(ctx, () -> { });
        verify(tasks).upsertCompleted(eq("abc"), eq(json), anyString(), anyString());
    }
}
