package hycurrency.cache

import kotlin.time.Duration

sealed interface CacheResult<out T> {
    data class Hit<T>(val data: T, val ttl: Duration? = null) : CacheResult<T>
    data object Miss : CacheResult<Nothing>
    data class Error(val cause: Throwable) : CacheResult<Nothing>
}

val <T> CacheResult<T>.isHit: Boolean get() = this is CacheResult.Hit
val <T> CacheResult<T>.isMiss: Boolean get() = this is CacheResult.Miss
val <T> CacheResult<T>.isError: Boolean get() = this is CacheResult.Error

fun <T> CacheResult<T>.getOrNull(): T? = (this as? CacheResult.Hit)?.data

fun <T> CacheResult<T>.getOrThrow(): T = when (this) {
    is CacheResult.Hit -> data
    is CacheResult.Miss -> throw NoSuchElementException("Cache miss")
    is CacheResult.Error -> throw cause
}

inline fun <T> CacheResult<T>.getOrElse(default: () -> T): T = when (this) {
    is CacheResult.Hit -> data
    else -> default()
}

inline fun <T, R> CacheResult<T>.map(transform: (T) -> R): CacheResult<R> = when (this) {
    is CacheResult.Hit -> CacheResult.Hit(transform(data), ttl)
    is CacheResult.Miss -> CacheResult.Miss
    is CacheResult.Error -> CacheResult.Error(cause)
}
