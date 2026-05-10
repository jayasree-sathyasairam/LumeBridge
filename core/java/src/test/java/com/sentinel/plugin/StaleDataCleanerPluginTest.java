package com.lumebridge.plugin;

import com.lumebridge.db.TaskRepository;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.*;

class StaleDataCleanerPluginTest {

    @Test
    void cleanCallsRepository() throws Exception {
        StaleDataCleanerPlugin cleaner = new StaleDataCleanerPlugin();
        TaskRepository mockRepo = mock(TaskRepository.class);
        
        // Inject mock repo
        try {
            java.lang.reflect.Field field = StaleDataCleanerPlugin.class.getDeclaredField("repository");
            field.setAccessible(true);
            field.set(cleaner, mockRepo);
        } catch (Exception e) {
            fail("Failed to inject mock repository");
        }

        // Invoke private clean method
        java.lang.reflect.Method cleanMethod = StaleDataCleanerPlugin.class.getDeclaredMethod("clean");
        cleanMethod.setAccessible(true);
        cleanMethod.invoke(cleaner);

        verify(mockRepo).deleteStale(anyInt(), anyInt());
    }
}
