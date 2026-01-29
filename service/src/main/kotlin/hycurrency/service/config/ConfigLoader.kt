package hycurrency.service.config

import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * Loads service configuration from environment variables and/or JSON files.
 */
object ConfigLoader {

    private val logger = LoggerFactory.getLogger(ConfigLoader::class.java)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Load configuration from environment variables.
     * Falls back to defaults for development.
     */
    fun loadFromEnv(): ServiceConfig {
        return ServiceConfig(
            server = ServerConfig(
                port = env("GRPC_PORT")?.toIntOrNull() ?: 50051,
                host = env("GRPC_HOST") ?: "0.0.0.0",
                enableReflection = env("GRPC_ENABLE_REFLECTION")?.toBoolean() ?: false
            ),
            database = DatabaseConfig(
                jdbcUrl = env("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/hycurrency",
                username = env("DATABASE_USER") ?: "postgres",
                password = env("DATABASE_PASSWORD") ?: "postgres",
                maxPoolSize = env("DATABASE_MAX_POOL_SIZE")?.toIntOrNull() ?: 10,
                minIdle = env("DATABASE_MIN_IDLE")?.toIntOrNull() ?: 2
            ),
            cache = CacheConfig(
                redisUrl = env("REDIS_URL") ?: "redis://localhost:6379",
                balanceTtl = env("CACHE_BALANCE_TTL_SECONDS")?.toLongOrNull()?.seconds ?: 5.seconds
            ),
            idempotency = IdempotencyConfig(
                ttl = env("IDEMPOTENCY_TTL_HOURS")?.toLongOrNull()?.hours ?: 1.hours
            ),
            currencies = loadCurrencyTypes(env("CURRENCY_CONFIG_PATH") ?: "currencies.json")
        )
    }

    /**
     * Load currency type definitions from JSON file.
     *
     * Looks for config in this order:
     * 1. File path specified by CURRENCY_CONFIG_PATH env var
     * 2. currencies.json in current working directory
     */
    private fun loadCurrencyTypes(configPath: String): List<CurrencyTypeConfig> {
        val file = File(configPath)
        if (file.exists()) {
            return loadFromFile(file)
        }

        throw IllegalStateException(
            "currencies.json not found. Provide via CURRENCY_CONFIG_PATH env var or place in working directory."
        )
    }

    private fun loadFromFile(file: File): List<CurrencyTypeConfig> {
        return try {
            val content = file.readText()
            val config = json.decodeFromString<CurrenciesFileConfig>(content)
            logger.info("Loaded {} currency types from {}", config.currencies.size, file.absolutePath)
            config.currencies
        } catch (e: Exception) {
            logger.error("Failed to parse currencies config from {}: {}", file.absolutePath, e.message)
            throw IllegalStateException("Failed to load currency configuration from ${file.absolutePath}", e)
        }
    }

    private fun env(name: String): String? = System.getenv(name)
}
