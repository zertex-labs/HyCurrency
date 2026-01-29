package hycurrency.service.repository

/**
 * Result type for repository operations.
 */
sealed interface RepositoryResult<out T> {
    data class Success<T>(val data: T) : RepositoryResult<T>
    data object NotFound : RepositoryResult<Nothing>
    data class UnknownCurrencyType(val currencyType: String) : RepositoryResult<Nothing>
    data class InsufficientFunds(
        val currencyType: String,
        val required: Long,
        val available: Long
    ) : RepositoryResult<Nothing>
    data class MaxBalanceExceeded(
        val currencyType: String,
        val maxBalance: Long,
        val currentBalance: Long,
        val attemptedAdd: Long
    ) : RepositoryResult<Nothing>
    data class Error(val cause: Throwable) : RepositoryResult<Nothing>
}

// Extension properties
val <T> RepositoryResult<T>.isSuccess: Boolean get() = this is RepositoryResult.Success
val <T> RepositoryResult<T>.isNotFound: Boolean get() = this is RepositoryResult.NotFound
val <T> RepositoryResult<T>.isUnknownCurrencyType: Boolean get() = this is RepositoryResult.UnknownCurrencyType
val <T> RepositoryResult<T>.isInsufficientFunds: Boolean get() = this is RepositoryResult.InsufficientFunds
val <T> RepositoryResult<T>.isMaxBalanceExceeded: Boolean get() = this is RepositoryResult.MaxBalanceExceeded
val <T> RepositoryResult<T>.isError: Boolean get() = this is RepositoryResult.Error

// Extension functions
fun <T> RepositoryResult<T>.getOrNull(): T? = (this as? RepositoryResult.Success)?.data

fun <T> RepositoryResult<T>.getOrThrow(): T = when (this) {
    is RepositoryResult.Success -> data
    is RepositoryResult.NotFound -> throw NoSuchElementException("Not found")
    is RepositoryResult.UnknownCurrencyType -> throw IllegalArgumentException("Unknown currency type: $currencyType")
    is RepositoryResult.InsufficientFunds -> throw IllegalStateException("Insufficient funds: required $required, available $available")
    is RepositoryResult.MaxBalanceExceeded -> throw IllegalStateException("Max balance exceeded: $maxBalance for $currencyType")
    is RepositoryResult.Error -> throw cause
}

inline fun <T, R> RepositoryResult<T>.map(transform: (T) -> R): RepositoryResult<R> = when (this) {
    is RepositoryResult.Success -> RepositoryResult.Success(transform(data))
    is RepositoryResult.NotFound -> RepositoryResult.NotFound
    is RepositoryResult.UnknownCurrencyType -> RepositoryResult.UnknownCurrencyType(currencyType)
    is RepositoryResult.InsufficientFunds -> RepositoryResult.InsufficientFunds(currencyType, required, available)
    is RepositoryResult.MaxBalanceExceeded -> RepositoryResult.MaxBalanceExceeded(currencyType, maxBalance, currentBalance, attemptedAdd)
    is RepositoryResult.Error -> RepositoryResult.Error(cause)
}

inline fun <T> RepositoryResult<T>.onSuccess(action: (T) -> Unit): RepositoryResult<T> {
    if (this is RepositoryResult.Success) action(data)
    return this
}
