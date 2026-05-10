package com.lumebridge.pipeline;

@FunctionalInterface
public interface MiddlewareFunc {
    void apply(RequestContext ctx, Runnable next) throws Exception;
}
