package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.pipeline.*;

import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

public class ConcurrencyGatePlugin implements Plugin {

    private Semaphore semaphore;
    private long delayMs;
    private int maxPermits = SentinelConstants.DEFAULT_THROTTLE_PERMITS;
    private final AtomicLong activeCount = new AtomicLong();
    private final AtomicLong totalProcessed = new AtomicLong();

    @Override public String name()  { return SentinelConstants.PLUGIN_CONCURRENCY_GATE; }
    @Override public Stage stage()  { return Stage.PRE_PROCESS; }
    @Override public int order()    { return 1; }

    @Override
    public void init(Map<String, String> config) {
        int permits = Integer.parseInt(config.getOrDefault(
                SentinelConstants.CORE_KEY_PERMITS,
                Integer.toString(SentinelConstants.DEFAULT_THROTTLE_PERMITS)));
        this.maxPermits = permits;
        this.delayMs = Long.parseLong(config.getOrDefault(
                SentinelConstants.CORE_KEY_DELAY_MS,
                Integer.toString(SentinelConstants.DEFAULT_WORK_DELAY_MS)));
        this.semaphore = new Semaphore(permits, true);
    }

    @Override
    public MiddlewareFunc middleware() {
        return (ctx, next) -> {
            long enqueued = System.currentTimeMillis();
            semaphore.acquire();
            long acquired = System.currentTimeMillis();
            activeCount.incrementAndGet();

            try {
                ctx.getMetrics().put(SentinelConstants.METRIC_GATE_WAIT_MS, acquired - enqueued);

                Thread.sleep(delayMs);

                next.run();

                totalProcessed.incrementAndGet();
                ctx.getMetrics().put(SentinelConstants.METRIC_GATE_WORK_MS, System.currentTimeMillis() - acquired);
            } finally {
                activeCount.decrementAndGet();
                semaphore.release();
            }
        };
    }

    public int getMaxPermits()       { return maxPermits; }
    public long getActiveCount()     { return activeCount.get(); }
    public long getTotalProcessed()  { return totalProcessed.get(); }
}
