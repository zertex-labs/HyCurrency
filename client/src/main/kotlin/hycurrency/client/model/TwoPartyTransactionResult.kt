package hycurrency.client.model

/**
 * Result of a successful currency transfer between two players.
 *
 * @property transactionId Unique ID of this transaction (for idempotency)
 * @property senderNewBalance The sender's balance after the transfer
 * @property receiverNewBalance The receiver's balance after the transfer
 * @property remainder The amount that could not be transferred due to clamping (0 if no clamping occurred).
 *                     Only applies if sender had insufficient funds with clamp mode.
 */
data class TwoPartyTransactionResult(
    val transactionId: String,
    val senderNewBalance: Long,
    val receiverNewBalance: Long,
    val remainder: Long
) {
    /** Returns true if the full requested amount was transferred */
    val wasFullyApplied: Boolean get() = remainder == 0L

    /** Returns true if some amount could not be transferred */
    val hasRemainder: Boolean get() = remainder > 0L
}
