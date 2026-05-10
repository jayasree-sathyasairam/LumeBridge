package com.lumebridge.pipeline;

import com.lumebridge.plugin.PayloadHasherPlugin;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class PipelineBuildTest {

    @Test
    void executeBeforeBuildThrows() {
        Pipeline p = new Pipeline();
        p.register(new PayloadHasherPlugin());
        assertThrows(IllegalStateException.class, () -> p.execute(new RequestContext("{}".getBytes())));
    }
}
