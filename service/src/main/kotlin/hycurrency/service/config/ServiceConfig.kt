package hycurrency.service.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Main configuration for the HyCurrency service.
 */
data class ServiceConfig(
    val server: ServerConfig,
    val database: DatabaseConfig,
    val cache: CacheConfig,
    val idempotency: IdempotencyConfig,
    val currencies: List<CurrencyTypeConfig>
)

data class ServerConfig(
    val port: Int = 50051,
    val host: String = "0.0.0.0",
    /** Enable gRPC reflection API. Should be false in production. */
    val enableReflection: Boolean = false
)

data class DatabaseConfig(
    val jdbcUrl: String,
    val username: String,
    val password: String,
    val maxPoolSize: Int = 10,
    val minIdle: Int = 2
)

data class CacheConfig(
    val redisUrl: String,
    val balanceTtl: Duration = 5.seconds
)

data class IdempotencyConfig(
    val ttl: Duration = 1.hours,
    val cleanupInterval: Duration = 15.minutes
)

/**
 * Defines how deduct operations behave when the player doesn't have enough balance.
 *
 * Example: Player has 10 coins, tries to deduct 100.
 */
@Serializable
enum class NegativeBehaviour {
    /**
     * Allow the balance to go negative.
     * Result: 10 - 100 = -90 (balance becomes -90)
     */
    @SerialName("allow")
    ALLOW,

    /**
     * Clamp to zero - deduct only what's available.
     * Result: 10 - 100 = 0 (deducts 10, remainder of 90 is returned in response)
     */
    @SerialName("clamp")
    CLAMP,

    /**
     * Strict - reject the transaction if insufficient funds.
     * Result: Returns InsufficientFundsError
     */
    @SerialName("strict")
    STRICT
}

/**
 * Defines how add operations behave when the result would exceed max_balance.
 */
@Serializable
enum class MaxBalanceBehaviour {
    /**
     * Clamp to max - add only what fits within the limit.
     * Result: 900 + 200 = 1000 (adds 100, remainder of 100 is returned in response)
     */
    @SerialName("clamp")
    CLAMP,

    /**
     * Strict - reject the transaction if it would exceed the limit.
     * Result: Returns an error (balance would exceed maximum)
     */
    @SerialName("strict")
    STRICT
}

/**
 * Configuration for a single currency type.
 * Currency types are defined in currencies.json(found via env CURRENCY_CONFIG_PATH)
 */
@Serializable
data class CurrencyTypeConfig(
    val id: String,

    @SerialName("display_name")
    val displayName: String,

    /**
     * Maximum balance a player can hold. Use -1 for unlimited.
     */
    @SerialName("max_balance")
    val maxBalance: Long = -1L,

    /**
     * How to handle deduct operations when player has insufficient funds.
     * See [NegativeBehaviour] for options.
     */
    @SerialName("negative_behaviour")
    val negativeBehaviour: NegativeBehaviour = NegativeBehaviour.STRICT,

    /**
     * How to handle add operations when result would exceed max_balance.
     * Only applies when max_balance is set (not -1).
     * See [MaxBalanceBehaviour] for options.
     */
    @SerialName("max_balance_behaviour")
    val maxBalanceBehaviour: MaxBalanceBehaviour = MaxBalanceBehaviour.CLAMP,

    /**
     * Whether players can transfer this currency to each other.
     */
    val transferable: Boolean = true,

    /**
     * Initial balance for new players. Defaults to 0.
     */
    @SerialName("starting_balance")
    val startingBalance: Long = 0L
) {
    /** Returns true if this currency enforces a maximum balance limit. */
    val enforcesMaxBalance: Boolean get() = maxBalance != -1L
}

/**
 * Wrapper for the currencies JSON file structure.
 */
@Serializable
data class CurrenciesFileConfig(
    val currencies: List<CurrencyTypeConfig>
)
