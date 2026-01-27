package hycurrency.client.model

sealed interface CurrencyResult<out T> {
    data class Success<T>(val data: T) : CurrencyResult<T>
    data class PlayerNotFound(val playerId: PlayerId) : CurrencyResult<Nothing>
    data class ReceiverNotFound(val playerId: PlayerId) : CurrencyResult<Nothing>
    data class InsufficientFunds(
        val playerId: PlayerId,
        val currencyType: String,
        val required: Long,
        val available: Long
    ) : CurrencyResult<Nothing>
    data class Error(val message: String, val cause: Throwable? = null) : CurrencyResult<Nothing>
}

// Extension properties for checking result type
val <T> CurrencyResult<T>.isSuccess: Boolean get() = this is CurrencyResult.Success
val <T> CurrencyResult<T>.isNotFound: Boolean get() = this is CurrencyResult.PlayerNotFound
val <T> CurrencyResult<T>.isReceiverNotFound: Boolean get() = this is CurrencyResult.ReceiverNotFound
val <T> CurrencyResult<T>.isInsufficientFunds: Boolean get() = this is CurrencyResult.InsufficientFunds
val <T> CurrencyResult<T>.isError: Boolean get() = this is CurrencyResult.Error

// Unwrapping functions
fun <T> CurrencyResult<T>.getOrNull(): T? = (this as? CurrencyResult.Success)?.data

fun <T> CurrencyResult<T>.getOrThrow(): T = when (this) {
    is CurrencyResult.Success -> data
    is CurrencyResult.PlayerNotFound -> throw NoSuchElementException("Player not found: $playerId")
    is CurrencyResult.ReceiverNotFound -> throw NoSuchElementException("Receiver not found: $playerId")
    is CurrencyResult.InsufficientFunds -> throw IllegalStateException(
        "Insufficient funds for $playerId: required $required $currencyType, available $available"
    )
    is CurrencyResult.Error -> throw cause ?: IllegalStateException(message)
}

inline fun <T> CurrencyResult<T>.getOrElse(default: () -> T): T = when (this) {
    is CurrencyResult.Success -> data
    else -> default()
}

// Transformation functions
inline fun <T, R> CurrencyResult<T>.map(transform: (T) -> R): CurrencyResult<R> = when (this) {
    is CurrencyResult.Success -> CurrencyResult.Success(transform(data))
    is CurrencyResult.PlayerNotFound -> this
    is CurrencyResult.ReceiverNotFound -> this
    is CurrencyResult.InsufficientFunds -> this
    is CurrencyResult.Error -> this
}

// Callback functions
inline fun <T> CurrencyResult<T>.onSuccess(action: (T) -> Unit): CurrencyResult<T> {
    if (this is CurrencyResult.Success) action(data)
    return this
}

inline fun <T> CurrencyResult<T>.onNotFound(action: (PlayerId) -> Unit): CurrencyResult<T> {
    if (this is CurrencyResult.PlayerNotFound) action(playerId)
    return this
}

inline fun <T> CurrencyResult<T>.onReceiverNotFound(action: (PlayerId) -> Unit): CurrencyResult<T> {
    if (this is CurrencyResult.ReceiverNotFound) action(playerId)
    return this
}

inline fun <T> CurrencyResult<T>.onInsufficientFunds(
    action: (playerId: PlayerId, currencyType: String, required: Long, available: Long) -> Unit
): CurrencyResult<T> {
    if (this is CurrencyResult.InsufficientFunds) action(playerId, currencyType, required, available)
    return this
}

inline fun <T> CurrencyResult<T>.onError(action: (String, Throwable?) -> Unit): CurrencyResult<T> {
    if (this is CurrencyResult.Error) action(message, cause)
    return this
}
