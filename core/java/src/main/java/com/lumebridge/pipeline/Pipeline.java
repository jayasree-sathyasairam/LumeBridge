package com.lumebridge.pipeline;

import java.util.*;

public class Pipeline {

    private final Map<Stage, List<Plugin>> stages = new EnumMap<>(Stage.class);
    private List<MiddlewareFunc> chain;

    public Pipeline() {
        for (Stage s : Stage.values()) {
            stages.put(s, new ArrayList<>());
        }
    }

    public void register(Plugin plugin) {
        stages.get(plugin.stage()).add(plugin);
    }

    /**
     * Sorts plugins within each stage by order, validates no duplicates,
     * and builds the flattened middleware chain.
     * Must be called after all plugins are registered and before execute().
     */
    public void build() {
        validateNoDuplicateOrder();

        for (List<Plugin> plugins : stages.values()) {
            plugins.sort(Comparator.comparingInt(Plugin::order));
        }

        List<MiddlewareFunc> middlewares = new ArrayList<>();
        for (Stage s : Stage.values()) {
            if (s == Stage.BACKGROUND) continue;
            for (Plugin p : stages.get(s)) {
                middlewares.add(p.middleware());
            }
        }
        this.chain = List.copyOf(middlewares);

        logRegistration();
    }

    /**
     * Executes the full middleware chain for a request.
     * Each middleware calls next to proceed; skipping next short-circuits.
     */
    public void execute(RequestContext ctx) throws Exception {
        if (chain == null) {
            throw new IllegalStateException("Pipeline.build() must be called before execute()");
        }
        runChain(ctx, 0);
    }

    private void runChain(RequestContext ctx, int index) throws Exception {
        if (index >= chain.size()) return;
        chain.get(index).apply(ctx, () -> {
            try {
                runChain(ctx, index + 1);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public List<Plugin> getBackgroundPlugins() {
        return Collections.unmodifiableList(stages.get(Stage.BACKGROUND));
    }

    public List<Plugin> allPlugins() {
        List<Plugin> all = new ArrayList<>();
        for (Stage s : Stage.values()) {
            all.addAll(stages.get(s));
        }
        return Collections.unmodifiableList(all);
    }

    private void validateNoDuplicateOrder() {
        for (Stage s : Stage.values()) {
            Set<Integer> seen = new HashSet<>();
            for (Plugin p : stages.get(s)) {
                if (!seen.add(p.order())) {
                    throw new IllegalStateException(
                        "Duplicate order %d in stage %s: plugin '%s' conflicts with an existing plugin"
                            .formatted(p.order(), s, p.name()));
                }
            }
        }
    }

    private void logRegistration() {
        System.out.println("Pipeline built:");
        for (Stage s : Stage.values()) {
            List<Plugin> plugins = stages.get(s);
            if (plugins.isEmpty()) continue;
            for (Plugin p : plugins) {
                System.out.printf("  %s [%d] %s%n", s, p.order(), p.name());
            }
        }
    }
}
