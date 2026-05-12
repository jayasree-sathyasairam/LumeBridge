package com.lumebridge;

/**
 * Central literals: plugin IDs (YAML), config keys, metadata keys, HTTP/JSON field names, and defaults.
 * Wire formats (DLQ JSON, HTTP responses) use {@code JSON_*}/{@code DLQ_JSON_*} so keys stay consistent.
 */
public final class SentinelConstants {

    private SentinelConstants() {}

    // --- Boolean strings (YAML / metadata) ---
    public static final String VAL_TRUE = "true";
    public static final String VAL_FALSE = "false";

    // --- Plugin IDs (must match {@code plugins.<id>} in YAML and {@link com.lumebridge.pipeline.Plugin#name()}) ---
    public static final String PLUGIN_PAYLOAD_NORMALIZER = "payload-normalizer";
    public static final String PLUGIN_VERSION_GUARD = "version-guard";
    public static final String PLUGIN_NONCE_ORDERING = "nonce-ordering";
    public static final String PLUGIN_COLLISION_DETECTION = "collision-detection";
    public static final String PLUGIN_TASK_PERSISTENCE = "task-persistence";
    public static final String PLUGIN_CIRCUIT_BREAKER = "circuit-breaker";
    public static final String PLUGIN_SMART_RETRY = "smart-retry";
    public static final String PLUGIN_DLQ = "dlq";
    public static final String PLUGIN_CONCURRENCY_GATE = "concurrency-gate";
    public static final String PLUGIN_PAYLOAD_HASHER = "payload-hasher";
    public static final String PLUGIN_DISTRIBUTED_LOCK = "distributed-lock";
    public static final String PLUGIN_DUAL_MODE_ROUTER = "dual-mode-router";
    public static final String PLUGIN_INTENT_CLASSIFIER = "intent-classifier";
    public static final String PLUGIN_SEMANTIC_CACHE = "semantic-cache";
    public static final String PLUGIN_PII_SCRUBBER = "pii-scrubber";
    public static final String PLUGIN_SAFETY_GUARDRAILS = "safety-guardrails";
    public static final String PLUGIN_RESPONSE_EVALUATOR = "response-evaluator";
    public static final String PLUGIN_API_KEY_AUTH = "api-key-auth";
    public static final String PLUGIN_CLIENT_QUOTA = "client-quota";
    public static final String PLUGIN_TOKEN_METER = "token-meter";
    public static final String PLUGIN_INTELLIGENT_ROUTER = "intelligent-router";

    /** {@code PROFILE} env values for {@link com.lumebridge.config.ConfigLoader}. */
    public static final String PROFILE_LIGHTWEIGHT = "lightweight";
    public static final String PROFILE_STANDARD = "standard";
    public static final String PROFILE_AI = "ai";

    // New granular profiles
    public static final String PROFILE_API_MINIMAL = "api-minimal"; // Core API only
    public static final String PROFILE_AI_MINIMAL = "ai-minimal";   // Core AI only
    public static final String PROFILE_API_PRO = "api-pro";         // API + Security + Resilience
    public static final String PROFILE_AI_PRO = "ai-pro";           // AI + Safety + Quality
    public static final String PROFILE_HYBRID = "hybrid";           // Full suite

    // --- Profile-only plugin IDs (YAML toggles; may not be implemented yet) ---
    public static final String PLUGIN_LOCK_METRICS = "lock-metrics";
    public static final String PLUGIN_STALE_DATA_CLEANER = "stale-data-cleaner";
    public static final String PLUGIN_METRICS_EXPORTER = "metrics-exporter";
    public static final String PLUGIN_TELEMETRY = "telemetry";

    // --- Core / merged config keys (flattened YAML + env) ---
    public static final String CORE_KEY_PORT = "port";
    public static final String CORE_KEY_PERMITS = "permits";
    public static final String CORE_KEY_DELAY_MS = "delay_ms";
    public static final String CORE_KEY_LOCK_TTL_SECONDS = "lock_ttl_seconds";
    public static final String CORE_KEY_REDIS_HOST = "redis_host";
    public static final String CORE_KEY_REDIS_PORT = "redis_port";
    public static final String CORE_KEY_POSTGRES_HOST = "postgres_host";
    public static final String CORE_KEY_POSTGRES_PORT = "postgres_port";
    public static final String CORE_KEY_POSTGRES_USER = "postgres_user";
    public static final String CORE_KEY_POSTGRES_PASSWORD = "postgres_password";
    public static final String CORE_KEY_POSTGRES_DB = "postgres_db";
    public static final String CORE_KEY_JDBC_URL = "jdbc_url";
    public static final String CORE_KEY_KAFKA_BOOTSTRAP_SERVERS = "kafka_bootstrap_servers";
    public static final String CORE_KEY_DLQ_TOPIC = "dlq_topic";
    public static final String CORE_KEY_PROFILE = "profile";

    public static final String CFG_KEY_ENABLED = "enabled";

    // --- Plugin config keys ---
    public static final String CFG_KEY_FAILURE_THRESHOLD = "failure_threshold";
    public static final String CFG_KEY_RECOVERY_TIMEOUT_MS = "recovery_timeout_ms";
    public static final String CFG_KEY_MAX_RETRIES = "max_retries";
    public static final String CFG_KEY_BASE_DELAY_MS = "base_delay_ms";
    public static final String CFG_KEY_MAX_DELAY_MS = "max_delay_ms";
    public static final String CFG_KEY_JITTER_PERCENT = "jitter_percent";
    public static final String CFG_KEY_RETRIABLE_MESSAGE_SUBSTRINGS = "retriable_message_substrings";
    public static final String CFG_KEY_MAX_RECORD_BYTES = "max_record_bytes";
    public static final String CFG_KEY_MODE = "mode";

    // --- Request metadata keys ---
    public static final String META_DLQ_SKIP = "dlq_skip";
    public static final String META_DLQ = "dlq";
    public static final String META_RETRY_AFTER_MS = "retry_after_ms";
    public static final String META_PERSIST_DONE = "persist_done";
    public static final String META_CIRCUIT = "circuit";
    /** Metadata value when circuit is open (paired with {@link #META_CIRCUIT}). */
    public static final String VAL_CIRCUIT_STATE_OPEN = "open";
    public static final String META_LOCK_KEY = "lock_key";
    public static final String META_LOCK_HOLDER = "lock_holder";
    public static final String META_CACHED_NONCE = "cached_nonce";
    public static final String META_INCOMING_NONCE = "incoming_nonce";
    public static final String META_STORED_RESPONSE_HASH = "stored_response_hash";
    public static final String META_INCOMING_RESPONSE_HASH = "incoming_response_hash";
    public static final String META_DB_VERSION = "db_version";

    // --- Request / pipeline status strings ---
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_ERROR = "error";
    public static final String STATUS_LOCKED = "locked";
    public static final String STATUS_CIRCUIT_OPEN = "circuit_open";
    public static final String STATUS_NONCE_REJECTED = "nonce_rejected";
    public static final String STATUS_COLLISION = "collision";
    public static final String STATUS_STALE_VERSION = "stale_version";

    // --- Collision detection {@code mode} values ---
    public static final String COLLISION_MODE_DISABLED = "disabled";
    public static final String COLLISION_MODE_MANUAL = "manual";
    public static final String COLLISION_MODE_AUTO_REPROCESS = "auto_reprocess";

    // --- JSON body field names (client payload) ---
    public static final String JSON_FIELD_NONCE = "nonce";
    public static final String JSON_FIELD_PROMPT = "prompt";
    public static final String JSON_FIELD_RESULT = "result";
    public static final String JSON_FIELD_EXPECTED_VERSION = "expected_version";
    public static final String JSON_FIELD_JSONRPC = "jsonrpc";
    public static final String JSON_JSONRPC_VERSION = "2.0";
    public static final String JSON_FIELD_CACHE_BYPASS = "cache_bypass";

    public static final String HEADER_MCP_PROTOCOL_VERSION = "MCP-Protocol-Version";
    public static final String HEADER_CONTENT_TYPE = "Content-Type";

    public static final String ROUTE_REST = "rest";
    public static final String ROUTE_MCP = "mcp";

    // --- Model Routes (Intelligent Router) ---
    public static final String ROUTE_GPT4_TURBO = "gpt-4-turbo";
    public static final String ROUTE_GPT4O = "gpt-4o";
    public static final String ROUTE_GPT4O_MINI = "gpt-4o-mini";
    public static final String ROUTE_CLAUDE_HAIKU = "claude-3-haiku";

    // --- Size Limits ---
    public static final int MAX_PAYLOAD_SIZE = 5000;

    public static final String INTENT_CACHE_ELIGIBLE = "cache_eligible";
    public static final String INTENT_MUST_EXECUTE = "must_execute";

    public static final String META_ROUTE = "route";
    public static final String META_INTENT = "intent";
    public static final String META_INTENT_CACHE_ELIGIBLE = "intent_cache_eligible";
    public static final String META_SEMANTIC_CACHE_HIT = "semantic_cache_hit";
    public static final String META_SEMANTIC_CACHE_SIMILARITY = "semantic_cache_similarity";
    public static final String META_SEMANTIC_CACHE_RESULT = "semantic_cache_result";
    public static final String META_SEMANTIC_CACHE_KEY = "semantic_cache_key";
    public static final String META_PII_SCRUB_COUNT = "pii_scrub_count";
    public static final String META_SAFETY_VIOLATION = "safety_violation";
    public static final String META_SAFETY_SCORE = "safety_score";
    public static final String META_HALLUCINATION_DETECTED = "hallucination_detected";
    public static final String META_FAITHFULNESS_SCORE = "faithfulness_score";
    public static final String META_AUTH_USER_ID = "auth_user_id";
    public static final String META_TOKENS_INPUT = "tokens_input";
    public static final String META_TOKENS_OUTPUT = "tokens_output";

    public static final String CFG_KEY_SIMILARITY_THRESHOLD = "similarity_threshold";
    public static final String CFG_KEY_EMBEDDING_DIMENSIONS = "embedding_dimensions";

    public static final double DEFAULT_SEMANTIC_SIMILARITY_THRESHOLD = 0.92;
    public static final int DEFAULT_SEMANTIC_EMBEDDING_DIMENSIONS = 64;

    public static final String ERR_SEMANTIC_CACHE_NO_DATASOURCE = "semantic-cache enabled but Postgres is not configured.";

    // --- HTTP API JSON keys (responses) ---
    public static final String JSON_KEY_STATUS = "status";
    public static final String JSON_KEY_ERROR = "error";
    public static final String JSON_KEY_PAYLOAD_HASH = "payload_hash";
    public static final String JSON_KEY_REQUEST_ID = "request_id";
    public static final String JSON_KEY_METRICS = "metrics";
    public static final String JSON_KEY_MAX_PERMITS = "max_permits";
    public static final String JSON_KEY_ACTIVE_PERMITS = "active_permits";
    public static final String JSON_KEY_TOTAL_PROCESSED = "total_processed";
    public static final String JSON_KEY_WARNINGS = "warnings";
    public static final String JSON_KEY_ROUTE = "route";
    public static final String JSON_KEY_INTENT = "intent";
    public static final String JSON_KEY_CACHE_ELIGIBLE = "cache_eligible";
    public static final String JSON_KEY_SEMANTIC_CACHE = "semantic_cache";
    public static final String JSON_KEY_HIT = "hit";
    public static final String JSON_KEY_SAFETY = "safety";
    public static final String JSON_KEY_EVALUATION = "evaluation";
    public static final String JSON_KEY_TOKENS = "tokens";
    public static final String JSON_KEY_INPUT = "input";
    public static final String JSON_KEY_OUTPUT = "output";

    public static final String HEALTH_VALUE_OK = "ok";

    public static final String LOG_SHUTDOWN_BEGIN = "Shutting down...";
    public static final String LOG_SHUTDOWN_DONE = "Shutdown complete.";
    public static final String LOG_ERR_CLOSING_PLUGIN_PREFIX = "Error closing plugin ";

    public static final String MSG_METHOD_NOT_ALLOWED = "method not allowed";
    public static final String MSG_EMPTY_PAYLOAD = "empty payload";

    // --- Metrics keys (RequestContext.metrics) ---
    public static final String METRIC_GATE_WAIT_MS = "gate_wait_ms";
    public static final String METRIC_GATE_WORK_MS = "gate_work_ms";

    // --- DLQ record JSON keys (Kafka value body) ---
    public static final String DLQ_JSON_PAYLOAD_HASH = "payload_hash";
    public static final String DLQ_JSON_REQUEST_ID = "request_id";
    public static final String DLQ_JSON_HTTP_STATUS = "http_status";
    public static final String DLQ_JSON_STATUS = "status";
    public static final String DLQ_JSON_ERROR = "error";
    public static final String DLQ_JSON_PAYLOAD_PREVIEW = "payload_preview";

    public static final String WARN_PAYLOAD_NORMALIZER_PREFIX = "payload_normalizer_skipped: ";

    public static final String VAL_LOCK_HOLDER_UNKNOWN = "unknown";

    public static final String SPLIT_PIPE_COMMA = "[|,]";

    public static final String TRUNCATION_SUFFIX = "...(truncated)";

    // --- Kafka producer (string values required by kafka-clients API) ---
    public static final String KAFKA_ACKS_ALL = "all";
    public static final String KAFKA_ENABLE_IDEMPOTENCE_FALSE = Boolean.FALSE.toString();
    public static final String KAFKA_CLIENT_ID_DLQ = "sentinel-dlq";

    // --- Redis ---
    public static final String DEFAULT_REDIS_HOST = "localhost";
    /** String form for merged config maps and {@link Integer#parseInt}. */
    public static final String DEFAULT_REDIS_PORT = "6379";
    public static final String REDIS_LOCK_KEY_PREFIX = "lock:";
    public static final String REDIS_NONCE_KEY_PREFIX = "nonce:";
    public static final int NONCE_KEY_TTL_SECONDS = 86_400;

    public static final String DEFAULT_POSTGRES_HOST = "localhost";
    public static final String DEFAULT_POSTGRES_DB = "sentinel_nexus";
    public static final String DEFAULT_POSTGRES_USER = "sentinel";
    public static final String DEFAULT_POSTGRES_PASSWORD = "sentinel";
    public static final String DEFAULT_POSTGRES_PORT = "5432";

    public static final String JDBC_PREFIX = "jdbc:postgresql://";

    public static final String DEFAULT_KAFKA_BOOTSTRAP_SERVERS = "localhost:19092";
    public static final String DEFAULT_DLQ_TOPIC = "sentinel-dlq";

    public static final String CONTENT_TYPE_JSON = "application/json";
    public static final String HTTP_POST = "POST";
    public static final String HTTP_GET = "GET";

    public static final String PATH_HEALTHZ = "/healthz";
    public static final String PATH_METRICS = "/metrics";
    public static final String PATH_TASK = "/task";

    public static final int DEFAULT_HTTP_PORT = 8080;
    public static final int DEFAULT_THROTTLE_PERMITS = 10;
    public static final int DEFAULT_WORK_DELAY_MS = 200;
    public static final int DEFAULT_LOCK_TTL_SECONDS = 30;

    public static final int DEFAULT_FAILURE_THRESHOLD = 5;
    public static final int DEFAULT_RECOVERY_TIMEOUT_MS = 60_000;
    public static final int DEFAULT_SMART_RETRY_MAX_RETRIES = 2;
    public static final int DEFAULT_SMART_RETRY_BASE_DELAY_MS = 50;
    public static final int DEFAULT_SMART_RETRY_MAX_DELAY_MS = 5_000;
    public static final int DEFAULT_SMART_RETRY_JITTER_PERCENT = 25;

    public static final int DEFAULT_MAX_RECORD_BYTES = 900_000;
    public static final int DEFAULT_DLQ_PAYLOAD_PREVIEW_MAX_CHARS = 16_384;

    public static final int HTTP_STATUS_OK = 200;
    public static final int HTTP_STATUS_BAD_REQUEST = 400;
    public static final int HTTP_STATUS_CONFLICT = 409;
    public static final int HTTP_STATUS_METHOD_NOT_ALLOWED = 405;
    public static final int HTTP_STATUS_INTERNAL_ERROR = 500;
    public static final int HTTP_STATUS_SERVICE_UNAVAILABLE = 503;

    public static final String MSG_EMPTY_PAYLOAD_FOR_HASHER = "empty payload";

    public static final int HI_MAX_POOL_SIZE = 10;
    public static final String HI_POOL_NAME = "sentinel-pool";
    public static final String HI_CACHE_PREP_STMTS = "cachePrepStmts";

    public static final String YAML_ROOT_CORE = "core";
    public static final String YAML_ROOT_PLUGINS = "plugins";
    public static final String YAML_ROOT_SERVER = "server";
    public static final String YAML_ROOT_REDIS = "redis";
    public static final String YAML_ROOT_POSTGRES = "postgres";
    public static final String YAML_ROOT_KAFKA = "kafka";

    public static final String YAML_NEST_PORT = "port";
    public static final String YAML_NEST_HOST = "host";
    public static final String YAML_NEST_USER = "user";
    public static final String YAML_NEST_PASSWORD = "password";
    public static final String YAML_NEST_DATABASE = "database";
    public static final String YAML_NEST_BOOTSTRAP_SERVERS = "bootstrap_servers";
    public static final String YAML_NEST_DLQ_TOPIC = "dlq_topic";

    public static final String ERR_VERSION_GUARD_NO_DATASOURCE = "version-guard enabled but Postgres plugins did not initialize a datasource.";
    public static final String ERR_COLLISION_NO_POSTGRES = "collision-detection enabled but Postgres is not configured.";
}
