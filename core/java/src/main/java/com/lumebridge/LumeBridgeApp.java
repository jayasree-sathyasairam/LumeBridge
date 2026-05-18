package com.lumebridge;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.lumebridge.config.ConfigLoader;
import com.lumebridge.db.Database;
import com.lumebridge.db.SemanticCacheRepository;
import com.lumebridge.db.TaskRepository;
import com.lumebridge.pipeline.Pipeline;
import com.lumebridge.pipeline.Plugin;
import com.lumebridge.pipeline.RequestContext;
import com.lumebridge.plugin.ApiKeyAuthPlugin;
import com.lumebridge.plugin.CircuitBreakerPlugin;
import com.lumebridge.plugin.ClientQuotaPlugin;
import com.lumebridge.plugin.CollisionDetectionPlugin;
import com.lumebridge.plugin.ConcurrencyGatePlugin;
import com.lumebridge.plugin.DistributedLockPlugin;
import com.lumebridge.plugin.DlqProducerPlugin;
import com.lumebridge.plugin.DualModeRouterPlugin;
import com.lumebridge.plugin.IntelligentRouterPlugin;
import com.lumebridge.plugin.IntentClassifierPlugin;
import com.lumebridge.plugin.LockMetricsPlugin;
import com.lumebridge.plugin.MetricsExporterPlugin;
import com.lumebridge.plugin.NonceOrderingPlugin;
import com.lumebridge.plugin.PIIScrubberPlugin;
import com.lumebridge.plugin.PayloadHasherPlugin;
import com.lumebridge.plugin.PayloadNormalizerPlugin;
import com.lumebridge.plugin.ResponseEvaluatorPlugin;
import com.lumebridge.plugin.SafetyGuardrailsPlugin;
import com.lumebridge.plugin.SemanticCachePlugin;
import com.lumebridge.plugin.SmartRetryPlugin;
import com.lumebridge.plugin.StaleDataCleanerPlugin;
import com.lumebridge.plugin.TaskPersistencePlugin;
import com.lumebridge.plugin.TelemetryPlugin;
import com.lumebridge.plugin.TokenMeterPlugin;
import com.lumebridge.plugin.VersionGuardPlugin;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.zaxxer.hikari.HikariDataSource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class LumeBridgeApp {

    private static final Gson GSON = new Gson();

    public static void main(String[] args) throws IOException {
        ConfigLoader config = ConfigLoader.load();

        int port = config.coreInt(SentinelConstants.CORE_KEY_PORT, SentinelConstants.DEFAULT_HTTP_PORT);

        boolean wantsTaskRepo = config.isPluginEnabled(SentinelConstants.PLUGIN_VERSION_GUARD)
                || config.isPluginEnabled(SentinelConstants.PLUGIN_COLLISION_DETECTION)
                || config.isPluginEnabled(SentinelConstants.PLUGIN_TASK_PERSISTENCE);
        boolean wantsSemanticCache = config.isPluginEnabled(SentinelConstants.PLUGIN_SEMANTIC_CACHE);

        HikariDataSource dataSource = null;
        TaskRepository taskRepository = null;
        if (wantsTaskRepo || wantsSemanticCache) {
            try {
                dataSource = Database.createDataSource(config.coreSettings());
            } catch (com.zaxxer.hikari.pool.HikariPool.PoolInitializationException e) {
                String jdbc = Database.resolveJdbcUrl(config.coreSettings());
                System.err.println();
                System.err.println("PostgreSQL is not reachable: " + jdbc);
                System.err.println("This profile needs Postgres (task persistence, collision detection, semantic cache, etc.).");
                System.err.println("From the repo root, start Docker infra first:");
                System.err.println("  make up");
                System.err.println("  or: docker compose -p lumebridge -f infra/docker-compose.yml up -d");
                System.err.println("Then: make verify-infra");
                System.err.println();
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                System.err.println(cause.getClass().getSimpleName() + ": " + cause.getMessage());
                System.exit(1);
            }
            if (wantsTaskRepo) {
                taskRepository = new TaskRepository(dataSource);
            }
        }

        ConcurrencyGatePlugin gatePlugin = new ConcurrencyGatePlugin();
        PayloadHasherPlugin hasherPlugin = new PayloadHasherPlugin();
        DistributedLockPlugin lockPlugin = new DistributedLockPlugin();

        Pipeline pipeline = new Pipeline();

        if (config.isPluginEnabled(SentinelConstants.PLUGIN_PAYLOAD_NORMALIZER)) {
            PayloadNormalizerPlugin normalizer = new PayloadNormalizerPlugin();
            normalizer.init(config.mergedConfig(SentinelConstants.PLUGIN_PAYLOAD_NORMALIZER));
            pipeline.register(normalizer);
        }

        if (config.isPluginEnabled(SentinelConstants.PLUGIN_PII_SCRUBBER)) {
            PIIScrubberPlugin pii = new PIIScrubberPlugin();
            pii.init(config.mergedConfig(SentinelConstants.PLUGIN_PII_SCRUBBER));
            pipeline.register(pii);
        }

        pipeline.register(gatePlugin);
        pipeline.register(hasherPlugin);
        pipeline.register(lockPlugin);

        if (config.isPluginEnabled(SentinelConstants.PLUGIN_VERSION_GUARD)) {
            if (taskRepository == null) {
                System.err.println(SentinelConstants.ERR_VERSION_GUARD_NO_DATASOURCE);
                System.exit(1);
            }
            VersionGuardPlugin vg = new VersionGuardPlugin(taskRepository);
            vg.init(config.mergedConfig(SentinelConstants.PLUGIN_VERSION_GUARD));
            pipeline.register(vg);
        }

        if (config.isPluginEnabled(SentinelConstants.PLUGIN_NONCE_ORDERING)) {
            NonceOrderingPlugin nonce = new NonceOrderingPlugin();
            nonce.init(config.mergedConfig(SentinelConstants.PLUGIN_NONCE_ORDERING));
            pipeline.register(nonce);
        }

        if (config.isPluginEnabled(SentinelConstants.PLUGIN_COLLISION_DETECTION)) {
            if (taskRepository == null) {
                System.err.println(SentinelConstants.ERR_COLLISION_NO_POSTGRES);
                System.exit(1);
            }
            CollisionDetectionPlugin collision = new CollisionDetectionPlugin(taskRepository);
            collision.init(config.mergedConfig(SentinelConstants.PLUGIN_COLLISION_DETECTION));
            pipeline.register(collision);
        }

        if (config.isPluginEnabled(SentinelConstants.PLUGIN_TASK_PERSISTENCE)) {
            TaskPersistencePlugin persist = new TaskPersistencePlugin(taskRepository);
            persist.init(config.mergedConfig(SentinelConstants.PLUGIN_TASK_PERSISTENCE));
            pipeline.register(persist);
        }

        if (config.isPluginEnabled(SentinelConstants.PLUGIN_DUAL_MODE_ROUTER)) {
            DualModeRouterPlugin router = new DualModeRouterPlugin();
            router.init(config.mergedConfig(SentinelConstants.PLUGIN_DUAL_MODE_ROUTER));
            pipeline.register(router);
        }

        if (config.isPluginEnabled(SentinelConstants.PLUGIN_SEMANTIC_CACHE)) {
            if (dataSource == null) {
                System.err.println(SentinelConstants.ERR_SEMANTIC_CACHE_NO_DATASOURCE);
                System.exit(1);
            }
            int dim = Integer.parseInt(config.mergedConfig(SentinelConstants.PLUGIN_SEMANTIC_CACHE).getOrDefault(
                    SentinelConstants.CFG_KEY_EMBEDDING_DIMENSIONS,
                    Integer.toString(SentinelConstants.DEFAULT_SEMANTIC_EMBEDDING_DIMENSIONS)));
            SemanticCacheRepository semRepo = new SemanticCacheRepository(dataSource, dim);
            SemanticCachePlugin sem = new SemanticCachePlugin(semRepo);
            sem.init(config.mergedConfig(SentinelConstants.PLUGIN_SEMANTIC_CACHE));
            pipeline.register(sem);
        }

        if (config.isPluginEnabled(SentinelConstants.PLUGIN_INTENT_CLASSIFIER)) {
            IntentClassifierPlugin intent = new IntentClassifierPlugin();
            intent.init(config.mergedConfig(SentinelConstants.PLUGIN_INTENT_CLASSIFIER));
            pipeline.register(intent);
        }

        if (config.isPluginEnabled(SentinelConstants.PLUGIN_CIRCUIT_BREAKER)) {
            CircuitBreakerPlugin cb = new CircuitBreakerPlugin();
            cb.init(config.mergedConfig(SentinelConstants.PLUGIN_CIRCUIT_BREAKER));
            pipeline.register(cb);
        }

        if (config.isPluginEnabled(SentinelConstants.PLUGIN_SMART_RETRY)) {
            SmartRetryPlugin retry = new SmartRetryPlugin();
            retry.init(config.mergedConfig(SentinelConstants.PLUGIN_SMART_RETRY));
            pipeline.register(retry);
        }

        if (config.isPluginEnabled(SentinelConstants.PLUGIN_DLQ)) {
            DlqProducerPlugin dlq = new DlqProducerPlugin();
            dlq.init(config.mergedConfig(SentinelConstants.PLUGIN_DLQ));
            pipeline.register(dlq);
        }

        if (config.isPluginEnabled(SentinelConstants.PLUGIN_API_KEY_AUTH)) {
            ApiKeyAuthPlugin auth = new ApiKeyAuthPlugin();
            auth.init(config.mergedConfig(SentinelConstants.PLUGIN_API_KEY_AUTH));
            pipeline.register(auth);
        }

        if (config.isPluginEnabled(SentinelConstants.PLUGIN_SAFETY_GUARDRAILS)) {
            SafetyGuardrailsPlugin safety = new SafetyGuardrailsPlugin();
            safety.init(config.mergedConfig(SentinelConstants.PLUGIN_SAFETY_GUARDRAILS));
            pipeline.register(safety);
        }

        if (config.isPluginEnabled(SentinelConstants.PLUGIN_CLIENT_QUOTA)) {
            ClientQuotaPlugin quota = new ClientQuotaPlugin();
            quota.init(config.mergedConfig(SentinelConstants.PLUGIN_CLIENT_QUOTA));
            pipeline.register(quota);
        }

        if (config.isPluginEnabled(SentinelConstants.PLUGIN_RESPONSE_EVALUATOR)) {
            ResponseEvaluatorPlugin eval = new ResponseEvaluatorPlugin();
            eval.init(config.mergedConfig(SentinelConstants.PLUGIN_RESPONSE_EVALUATOR));
            pipeline.register(eval);
        }

        if (config.isPluginEnabled(SentinelConstants.PLUGIN_LOCK_METRICS)) {
            LockMetricsPlugin lockMetrics = new LockMetricsPlugin();
            lockMetrics.init(config.mergedConfig(SentinelConstants.PLUGIN_LOCK_METRICS));
            pipeline.register(lockMetrics);
        }

        if (config.isPluginEnabled(SentinelConstants.PLUGIN_STALE_DATA_CLEANER)) {
            StaleDataCleanerPlugin cleaner = new StaleDataCleanerPlugin();
            cleaner.init(config.mergedConfig(SentinelConstants.PLUGIN_STALE_DATA_CLEANER));
            pipeline.register(cleaner);
        }

        MetricsExporterPlugin metricsExporter = null;
        if (config.isPluginEnabled(SentinelConstants.PLUGIN_METRICS_EXPORTER)) {
            metricsExporter = new MetricsExporterPlugin();
            metricsExporter.init(config.mergedConfig(SentinelConstants.PLUGIN_METRICS_EXPORTER));
            pipeline.register(metricsExporter);
        }

        if (config.isPluginEnabled(SentinelConstants.PLUGIN_TOKEN_METER)) {
            TokenMeterPlugin tokenMeter = new TokenMeterPlugin();
            tokenMeter.init(config.mergedConfig(SentinelConstants.PLUGIN_TOKEN_METER));
            pipeline.register(tokenMeter);
        }

        if (config.isPluginEnabled(SentinelConstants.PLUGIN_INTELLIGENT_ROUTER)) {
            IntelligentRouterPlugin intelligentRouter = new IntelligentRouterPlugin();
            intelligentRouter.init(config.mergedConfig(SentinelConstants.PLUGIN_INTELLIGENT_ROUTER));
            pipeline.register(intelligentRouter);
        }

        if (config.isPluginEnabled(SentinelConstants.PLUGIN_TELEMETRY)) {
            TelemetryPlugin telemetry = new TelemetryPlugin();
            telemetry.init(config.mergedConfig(SentinelConstants.PLUGIN_TELEMETRY));
            pipeline.register(telemetry);
        }

        gatePlugin.init(config.mergedConfig(SentinelConstants.PLUGIN_CONCURRENCY_GATE));
        hasherPlugin.init(config.mergedConfig(SentinelConstants.PLUGIN_PAYLOAD_HASHER));
        lockPlugin.init(config.mergedConfig(SentinelConstants.PLUGIN_DISTRIBUTED_LOCK));

        pipeline.build();

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        server.createContext(SentinelConstants.PATH_TASK, exchange -> handleTask(exchange, pipeline));
        
        // Benchmark endpoints
        server.createContext("/gate", exchange -> handleTask(exchange, pipeline));
        server.createContext("/lock", exchange -> handleTask(exchange, pipeline));

        server.createContext(SentinelConstants.PATH_HEALTHZ, exchange ->
            sendJson(exchange, SentinelConstants.HTTP_STATUS_OK,
                Map.of(SentinelConstants.JSON_KEY_STATUS, SentinelConstants.HEALTH_VALUE_OK))
        );

        MetricsExporterPlugin exporterFinal = metricsExporter;
        server.createContext(SentinelConstants.PATH_METRICS, exchange -> {
            Map<String, Object> metrics = new LinkedHashMap<>();
            metrics.put(SentinelConstants.JSON_KEY_MAX_PERMITS, gatePlugin.getMaxPermits());
            metrics.put(SentinelConstants.JSON_KEY_ACTIVE_PERMITS, gatePlugin.getActiveCount());
            metrics.put(SentinelConstants.JSON_KEY_TOTAL_PROCESSED, gatePlugin.getTotalProcessed());
            
            if (exporterFinal != null) {
                metrics.put("exporter", exporterFinal.collectAll());
            }
            sendJson(exchange, SentinelConstants.HTTP_STATUS_OK, metrics);
        });

        HikariDataSource dsFinal = dataSource;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println(SentinelConstants.LOG_SHUTDOWN_BEGIN);
            for (Plugin p : pipeline.allPlugins()) {
                try {
                    p.close();
                } catch (Exception e) {
                    System.err.println(SentinelConstants.LOG_ERR_CLOSING_PLUGIN_PREFIX + p.name() + ": " + e.getMessage());
                }
            }
            if (dsFinal != null) {
                dsFinal.close();
            }
            server.stop(2);
            System.out.println(SentinelConstants.LOG_SHUTDOWN_DONE);
        }));

        server.start();
        System.out.printf("lumebridge listening on :%d%n", port);
        System.out.printf("  POST %s  — full pipeline%n", SentinelConstants.PATH_TASK);
    }

    private static void handleTask(HttpExchange exchange, Pipeline pipeline) throws IOException {
        if (!SentinelConstants.HTTP_POST.equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, SentinelConstants.HTTP_STATUS_METHOD_NOT_ALLOWED,
                Map.of(SentinelConstants.JSON_KEY_ERROR, SentinelConstants.MSG_METHOD_NOT_ALLOWED));
            return;
        }

        byte[] body = exchange.getRequestBody().readAllBytes();
        if (body.length == 0) {
            sendJson(exchange, SentinelConstants.HTTP_STATUS_BAD_REQUEST,
                Map.of(SentinelConstants.JSON_KEY_ERROR, SentinelConstants.MSG_EMPTY_PAYLOAD));
            return;
        }

        RequestContext ctx = new RequestContext(body);
        exchange.getRequestHeaders().forEach((name, values) -> {
            if (values != null && !values.isEmpty()) {
                ctx.putRequestHeader(name, values.getFirst());
            }
        });

        try {
            pipeline.execute(ctx);
        } catch (Exception e) {
            Throwable cause = e instanceof RuntimeException && e.getCause() != null ? e.getCause() : e;
            ctx.setHttpStatus(SentinelConstants.HTTP_STATUS_INTERNAL_ERROR);
            ctx.setStatus(SentinelConstants.STATUS_ERROR);
            ctx.setError(cause instanceof Exception ex ? ex : new RuntimeException(cause));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put(SentinelConstants.JSON_KEY_STATUS, ctx.getStatus());

        if (ctx.getPayloadHash() != null) {
            response.put(SentinelConstants.JSON_KEY_PAYLOAD_HASH, ctx.getPayloadHash());
        }
        if (ctx.getRequestId() != null) {
            response.put(SentinelConstants.JSON_KEY_REQUEST_ID, ctx.getRequestId());
        }
        if (!ctx.getMetrics().isEmpty()) {
            response.put(SentinelConstants.JSON_KEY_METRICS, ctx.getMetrics());
        }
        if (!ctx.getWarnings().isEmpty()) {
            response.put(SentinelConstants.JSON_KEY_WARNINGS, ctx.getWarnings());
        }
        if (ctx.getError() != null) {
            response.put(SentinelConstants.JSON_KEY_ERROR, ctx.getError().getMessage());
        }
        if (ctx.getHttpStatus() == SentinelConstants.HTTP_STATUS_CONFLICT && !ctx.getMetadata().isEmpty()) {
            for (var e : ctx.getMetadata().entrySet()) {
                response.put(e.getKey(), e.getValue());
            }
        } else {
            enrichIntelligenceResponse(ctx, response);
        }

        sendJson(exchange, ctx.getHttpStatus(), response);
    }

    private static void enrichIntelligenceResponse(RequestContext ctx, Map<String, Object> response) {
        Map<String, String> meta = ctx.getMetadata();
        if (meta.containsKey(SentinelConstants.META_ROUTE)) {
            response.put(SentinelConstants.JSON_KEY_ROUTE, meta.get(SentinelConstants.META_ROUTE));
        }
        if (meta.containsKey(SentinelConstants.META_INTENT)) {
            response.put(SentinelConstants.JSON_KEY_INTENT, meta.get(SentinelConstants.META_INTENT));
        }
        if (meta.containsKey(SentinelConstants.META_INTENT_CACHE_ELIGIBLE)) {
            response.put(SentinelConstants.JSON_KEY_CACHE_ELIGIBLE,
                    SentinelConstants.VAL_TRUE.equalsIgnoreCase(meta.get(SentinelConstants.META_INTENT_CACHE_ELIGIBLE)));
        }
        if (SentinelConstants.VAL_TRUE.equalsIgnoreCase(meta.get(SentinelConstants.META_SEMANTIC_CACHE_HIT))) {
            Map<String, Object> sc = new LinkedHashMap<>();
            sc.put(SentinelConstants.JSON_KEY_HIT, true);
            String sim = meta.get(SentinelConstants.META_SEMANTIC_CACHE_SIMILARITY);
            if (sim != null) {
                try {
                    sc.put(SentinelConstants.META_SEMANTIC_CACHE_SIMILARITY, Double.parseDouble(sim));
                } catch (NumberFormatException e) {
                    sc.put(SentinelConstants.META_SEMANTIC_CACHE_SIMILARITY, sim);
                }
            }
            if (meta.containsKey(SentinelConstants.META_SEMANTIC_CACHE_KEY)) {
                sc.put(SentinelConstants.META_SEMANTIC_CACHE_KEY, meta.get(SentinelConstants.META_SEMANTIC_CACHE_KEY));
            }
            String raw = meta.get(SentinelConstants.META_SEMANTIC_CACHE_RESULT);
            if (raw != null) {
                try {
                    JsonElement el = JsonParser.parseString(raw);
                    sc.put(SentinelConstants.JSON_FIELD_RESULT, el);
                } catch (Exception e) {
                    sc.put(SentinelConstants.JSON_FIELD_RESULT, raw);
                }
            }
            response.put(SentinelConstants.JSON_KEY_SEMANTIC_CACHE, sc);
        }
        String scrub = meta.get(SentinelConstants.META_PII_SCRUB_COUNT);
        if (scrub != null) {
            response.put(SentinelConstants.META_PII_SCRUB_COUNT, Integer.parseInt(scrub));
        }

        if (meta.containsKey(SentinelConstants.META_SAFETY_VIOLATION)) {
            response.put(SentinelConstants.JSON_KEY_SAFETY, Map.of(
                "violation", SentinelConstants.VAL_TRUE.equalsIgnoreCase(meta.get(SentinelConstants.META_SAFETY_VIOLATION))
            ));
        }

        if (meta.containsKey(SentinelConstants.META_HALLUCINATION_DETECTED)) {
            response.put(SentinelConstants.JSON_KEY_EVALUATION, Map.of(
                "hallucination_detected", SentinelConstants.VAL_TRUE.equalsIgnoreCase(meta.get(SentinelConstants.META_HALLUCINATION_DETECTED))
            ));
        }

        if (meta.containsKey(SentinelConstants.META_TOKENS_INPUT)) {
            Map<String, Object> tokens = new LinkedHashMap<>();
            tokens.put(SentinelConstants.JSON_KEY_INPUT, Integer.parseInt(meta.get(SentinelConstants.META_TOKENS_INPUT)));
            if (meta.containsKey(SentinelConstants.META_TOKENS_OUTPUT)) {
                tokens.put(SentinelConstants.JSON_KEY_OUTPUT, Integer.parseInt(meta.get(SentinelConstants.META_TOKENS_OUTPUT)));
            }
            response.put(SentinelConstants.JSON_KEY_TOKENS, tokens);
        }
    }

    private static void sendJson(HttpExchange exchange, int status, Map<String, Object> body) throws IOException {
        String json = GSON.toJson(body);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", SentinelConstants.CONTENT_TYPE_JSON);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
