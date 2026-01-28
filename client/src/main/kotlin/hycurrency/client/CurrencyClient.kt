package hycurrency.client

import hycurrency.client.model.CurrencyResult
import hycurrency.client.model.PlayerId
import hycurrency.client.model.TransactionResult
import hycurrency.client.model.TwoPartyTransactionResult

/**
 * Client interface for currency operations.
 *
 * All mutation operations require a `transactionId` for idempotency.
 * If the same `transactionId` is used twice, the second call will
 * return the result of the first operation without applying changes again.
 */
interface CurrencyClient : AutoCloseable {
    // --- Query Operations ---

    /**
     * Get the balance of a specific currency type for a player.
     * Returns 0 if the player exists but has no balance for this currency type.
     */
    suspend fun getBalance(playerId: PlayerId, currencyType: String): CurrencyResult<Long>

    /**
     * Get all currency balances for a player.
     * Returns a map of currency type to amount.
     */
    suspend fun getAllBalances(playerId: PlayerId): CurrencyResult<Map<String, Long>>

    // --- Player Operations ---

    /**
     * Deduct currency from a player's balance.
     *
     * @param transactionId Unique ID for idempotency (make sure it's deterministic, otherwise it's useless)
     * @param playerId The player to deduct from
     * @param currencyType The type of currency (e.g., "coins", "gems")
     * @param amount The amount to deduct (must be positive)
     * @param reason Optional reason for tracking (e.g., "shop_purchase:diamond_sword")
     * @return TransactionResult with new balance and transaction ID, or an error result
     */
    suspend fun deduct(
        transactionId: String,
        playerId: PlayerId,
        currencyType: String,
        amount: Long,
        reason: String? = null
    ): CurrencyResult<TransactionResult>

    /**
     * Add currency to a player's balance.
     *
     * @param transactionId Unique ID for idempotency (make sure it's deterministic, otherwise it's useless)
     * @param playerId The player to add to
     * @param currencyType The type of currency (e.g., "coins", "gems")
     * @param amount The amount to add (must be positive)
     * @param reason Optional reason for tracking (e.g., "quest_reward:kill_dragon")
     * @return TransactionResult with new balance and transaction ID, or an error result
     */
    suspend fun add(
        transactionId: String,
        playerId: PlayerId,
        currencyType: String,
        amount: Long,
        reason: String? = null
    ): CurrencyResult<TransactionResult>

    /**
     * Transfer currency from one player to another.
     *
     * @param transactionId Unique ID for idempotency (make sure it's deterministic, otherwise it's useless)
     * @param fromPlayerId The player to transfer from
     * @param toPlayerId The player to transfer to
     * @param currencyType The type of currency
     * @param amount The amount to transfer (must be positive)
     * @param reason Optional reason for tracking (e.g., "player_trade")
     * @return TwoPartyTransactionResult with sender and receiver's new balance and transaction ID, or an error result
     */
    suspend fun transfer(
        transactionId: String,
        fromPlayerId: PlayerId,
        toPlayerId: PlayerId,
        currencyType: String,
        amount: Long,
        reason: String? = null
    ): CurrencyResult<TwoPartyTransactionResult>
}
