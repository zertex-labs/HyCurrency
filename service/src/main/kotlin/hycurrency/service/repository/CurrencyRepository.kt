package hycurrency.service.repository

import java.util.UUID

/**
 * Result of a mutation operation that may have been clamped.
 *
 * @property newBalance The player's new balance after the operation
 * @property remainder Amount that could not be applied due to clamping (0 if fully applied)
 */
data class MutationResult(
    val newBalance: Long,
    val remainder: Long = 0L
) {
    val wasFullyApplied: Boolean get() = remainder == 0L
    val wasClamped: Boolean get() = remainder > 0L
}

/**
 * Result of a transfer operation that may have been clamped.
 */
data class TransferMutationResult(
    val senderNewBalance: Long,
    val receiverNewBalance: Long,
    val remainder: Long = 0L
) {
    val wasFullyApplied: Boolean get() = remainder == 0L
    val wasClamped: Boolean get() = remainder > 0L
}

/**
 * Repository interface for currency balance operations.
 */
interface CurrencyRepository {

    /**
     * Get the balance for a player and currency type.
     * Returns the starting_balance from config if the player has no balance record.
     */
    suspend fun getBalance(playerId: UUID, currencyType: String): RepositoryResult<Long>

    /**
     * Get all balances for a player.
     * Returns an empty map if the player has no balance records.
     */
    suspend fun getAllBalances(playerId: UUID): RepositoryResult<Map<String, Long>>

    /**
     * Add amount to a player's balance (creates record if doesn't exist).
     *
     * Behavior depends on currency's maxBalanceBehaviour:
     * - CLAMP: Adds only what fits within max_balance, returns remainder
     * - STRICT: Returns error if result would exceed max_balance
     *
     * @return MutationResult with new balance and any remainder
     */
    suspend fun add(
        playerId: UUID,
        currencyType: String,
        amount: Long
    ): RepositoryResult<MutationResult>

    /**
     * Deduct amount from a player's balance.
     *
     * Behavior depends on currency's negativeBehaviour:
     * - ALLOW: Balance can go negative
     * - CLAMP: Deducts only what's available, returns remainder
     * - STRICT: Returns InsufficientFunds if balance < amount
     *
     * @return MutationResult with new balance and any remainder
     */
    suspend fun deduct(
        playerId: UUID,
        currencyType: String,
        amount: Long
    ): RepositoryResult<MutationResult>

    /**
     * Transfer amount from one player to another atomically.
     *
     * @return TransferMutationResult with both new balances and any remainder
     */
    suspend fun transfer(
        fromPlayerId: UUID,
        toPlayerId: UUID,
        currencyType: String,
        amount: Long
    ): RepositoryResult<TransferMutationResult>

    /**
     * Set a player's balance to an exact value (admin operation).
     * Ignores max_balance and negative_behaviour constraints.
     * Returns the new balance.
     */
    suspend fun setBalance(
        playerId: UUID,
        currencyType: String,
        amount: Long
    ): RepositoryResult<Long>
}
