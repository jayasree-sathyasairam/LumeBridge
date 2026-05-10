package com.lumebridge.pipeline;

import java.util.Map;

public interface Plugin {

    String name();

    Stage stage();

    int order();

    default void init(Map<String, String> config) {}

    MiddlewareFunc middleware();

    default void close() {}
}
