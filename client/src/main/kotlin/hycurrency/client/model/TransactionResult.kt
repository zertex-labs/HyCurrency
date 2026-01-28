package hycurrency.client.model

/**
 * Result of a successful currency mutation operation.
 *
 * @property transactionId Unique ID of this transaction (for idempotency)
 * @property newBalance The player's balance after the operation
 * @property remainder The amount that could not be applied due to clamping (0 if no clamping occurred).
 *                     For deduct: remainder when balance would go negative with clamp mode.
 *                     For add: remainder when balance would exceed max_balance with clamp mode.
 */
data class TransactionResult(
    val transactionId: String,
    val newBalance: Long,
    val remainder: Long
) {
    /** Returns true if the full requested amount was applied (no remainder) */
    val wasFullyApplied: Boolean get() = remainder == 0L

    /** Returns true if some amount could not be applied (has remainder) */
    val hasRemainder: Boolean get() = remainder > 0L
}
