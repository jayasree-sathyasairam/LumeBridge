package com.lumebridge.plugin;

import com.lumebridge.SentinelConstants;
import com.lumebridge.ai.EmbeddingUtil;
import com.lumebridge.cache.SemanticCacheContextBuilder;
import com.lumebridge.db.SemanticCacheRepository;
import com.lumebridge.intent.QueryIntent;
import com.lumebridge.pipeline.MiddlewareFunc;
import com.lumebridge.pipeline.Plugin;
import com.lumebridge.pipeline.Stage;

import java.util.Map;

/**
 * pgvector nearest-neighbour lookup using deterministic pseudo-embeddings; on hit, exposes cached JSON in metadata
 * for the HTTP layer ({@link com.lumebridge.LumeBridgeApp}).
 * <p>V2 P0/P1: scoped by tenant fingerprint and freshness bucket (prompt heuristics and/or {@link QueryIntent}).</p>
 */
public class SemanticCachePlugin implements Plugin {

    private SemanticCacheRepository repository;
    private double similarityThreshold = SentinelConstants.DEFAULT_SEMANTIC_SIMILARITY_THRESHOLD;

    public SemanticCachePlugin(SemanticCacheRepository repository) {
        this.repository = repository;
    }

    @Override public String name() { return SentinelConstants.PLUGIN_SEMANTIC_CACHE; }
    @Override public Stage stage() { return Stage.ROUTE; }
    @Override public int order() { return 3; }

    @Override
    public void init(Map<String, String> config) {
        this.similarityThreshold = Double.parseDouble(config.getOrDefault(
                SentinelConstants.CFG_KEY_SIMILARITY_THRESHOLD,
                Double.toString(SentinelConstants.DEFAULT_SEMANTIC_SIMILARITY_THRESHOLD)));
    }

    @Override
    public MiddlewareFunc middleware() {
        return (ctx, next) -> {
            if (repository == null) {
                next.run();
                return;
            }
            try {
                byte[] raw = ctx.getRawPayload();
                String eligible = ctx.getMetadata().get(SentinelConstants.META_INTENT_CACHE_ELIGIBLE);
                if (SentinelConstants.VAL_FALSE.equalsIgnoreCase(eligible)) {
                    next.run();
                    return;
                }
                String apiKey = ctx.getRequestHeader(SentinelConstants.HEADER_API_KEY);
                String tenant = SemanticCacheContextBuilder.tenantFingerprint(apiKey);
                String freshness = resolveFreshnessBucket(ctx, raw);
                String scope = SemanticCacheContextBuilder.cacheScope(tenant, freshness);
                byte[] material = SemanticCacheContextBuilder.embeddingMaterial(raw, tenant, freshness);
                float[] query = EmbeddingUtil.unitEmbeddingFromPayload(material, repository.embeddingDimensions());
                var hit = repository.findClosest(query, similarityThreshold, scope);
                if (hit.isPresent()) {
                    var h = hit.get();
                    ctx.getMetadata().put(SentinelConstants.META_SEMANTIC_CACHE_HIT, SentinelConstants.VAL_TRUE);
                    ctx.getMetadata().put(SentinelConstants.META_SEMANTIC_CACHE_SIMILARITY, String.valueOf(h.similarity()));
                    ctx.getMetadata().put(SentinelConstants.META_SEMANTIC_CACHE_RESULT, h.cachedResponseJson());
                    ctx.getMetadata().put(SentinelConstants.META_SEMANTIC_CACHE_KEY, h.cacheKey());
                }
            } catch (Exception e) {
                ctx.getWarnings().add("semantic_cache_skipped: " + e.getMessage());
            }
            next.run();
        };
    }

    private static String resolveFreshnessBucket(com.lumebridge.pipeline.RequestContext ctx, byte[] raw) {
        String qi = ctx.getMetadata().get(SentinelConstants.META_QUERY_INTENT);
        if (qi != null && !qi.isBlank()) {
            try {
                QueryIntent intent = QueryIntent.valueOf(qi.trim());
                return SemanticCacheContextBuilder.freshnessBucketForIntent(intent);
            } catch (IllegalArgumentException ignored) {
                // fall through to heuristic bucket
            }
        }
        return SemanticCacheContextBuilder.freshnessBucket(raw);
    }
}
