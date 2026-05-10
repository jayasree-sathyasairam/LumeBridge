package com.lumebridge.pipeline;

import com.lumebridge.plugin.PayloadHasherPlugin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class PipelineDuplicateOrderTest {

    @Test
    void duplicateOrderInSameStageFailsBuild() {
        PayloadHasherPlugin h1 = new PayloadHasherPlugin();
        PayloadHasherPlugin h2 = new PayloadHasherPlugin();
        Pipeline p = new Pipeline();
        p.register(h1);
        p.register(h2);
        assertThrows(IllegalStateException.class, p::build);
    }
}
