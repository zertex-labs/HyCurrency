package hycurrency.client.model

/**
 * Result of a successful currency mutation operation.
 */
data class TransactionResult(
    val newBalance: Long,
    val transactionId: String
)