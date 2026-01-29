package hycurrency.service.idempotency

/**
 * Result of checking/storing an idempotency key.
 */
sealed interface IdempotencyResult<out T> {
    /**
     * This is a new transaction ID - proceed with the operation.
     */
    data object New : IdempotencyResult<Nothing>

    /**
     * This transaction ID was already processed - return the cached result.
     */
    data class Duplicate<T>(val cachedResult: T) : IdempotencyResult<T>

    /**
     * Error checking idempotency store.
     */
    data class Error(val cause: Throwable) : IdempotencyResult<Nothing>
}

val <T> IdempotencyResult<T>.isNew: Boolean get() = this is IdempotencyResult.New
val <T> IdempotencyResult<T>.isDuplicate: Boolean get() = this is IdempotencyResult.Duplicate
val <T> IdempotencyResult<T>.isError: Boolean get() = this is IdempotencyResult.Error
