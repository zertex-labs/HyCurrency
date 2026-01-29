package hycurrency.service.idempotency

import kotlin.time.Duration

/**
 * Store for tracking processed transactions to ensure idempotency.
 *
 * Flow:
 * 1. Call check() before processing a transaction
 * 2. If New, process the transaction
 * 3. Call store() after successful processing
 * 4. If Duplicate, return the cached result without processing
 */
interface IdempotencyStore {

    /**
     * Check if a transaction ID has already been processed.
     *
     * @param transactionId The unique transaction identifier
     * @return New if not processed, Duplicate with cached result if already processed
     */
    suspend fun <T> check(transactionId: String, deserialize: (String) -> T): IdempotencyResult<T>

    /**
     * Store a processed transaction result.
     *
     * @param transactionId The unique transaction identifier
     * @param result The result to cache
     * @param ttl How long to keep the record (for cleanup)
     * @param serialize Function to serialize the result to a string
     * @return true if stored successfully
     */
    suspend fun <T> store(
        transactionId: String,
        result: T,
        ttl: Duration,
        serialize: (T) -> String
    ): Boolean

    /**
     * Cleanup expired transaction records.
     * Should be called periodically by a background job.
     */
    suspend fun cleanup(): Int
}
