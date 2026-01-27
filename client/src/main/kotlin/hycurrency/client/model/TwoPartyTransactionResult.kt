package hycurrency.client.model

/**
 * Result of a successful currency mutation operation between two players
 */
data class TwoPartyTransactionResult(
    val transactionId: String,
    val senderNewBalance: Long,
    val receiverNewBalance: Long
)