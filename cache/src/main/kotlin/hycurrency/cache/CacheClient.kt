package hycurrency.cache

import kotlin.time.Duration

interface CacheClient : AutoCloseable {
    suspend fun get(key: String): CacheResult<ByteArray>
    suspend fun mget(vararg keys: String): Map<String, CacheResult<ByteArray>>
    suspend fun set(key: String, value: ByteArray, ttl: Duration? = null): Boolean
    suspend fun mset(entries: Map<String, ByteArray>, ttl: Duration? = null): Boolean
    suspend fun delete(key: String): Boolean
    suspend fun delete(vararg keys: String): Long
    suspend fun exists(key: String): Boolean
    suspend fun expire(key: String, ttl: Duration): Boolean
    suspend fun ttl(key: String): Duration?
}

suspend inline fun <reified T> CacheClient.getAs(
    key: String,
    deserialize: (ByteArray) -> T
): CacheResult<T> = get(key).map(deserialize)

suspend inline fun <reified T> CacheClient.setAs(
    key: String,
    value: T,
    ttl: Duration? = null,
    serialize: (T) -> ByteArray
): Boolean = set(key, serialize(value), ttl)

suspend inline fun CacheClient.getOrSet(
    key: String,
    ttl: Duration? = null,
    loader: () -> ByteArray
): ByteArray = when (val result = get(key)) {
    is CacheResult.Hit -> result.data
    is CacheResult.Miss -> loader().also { set(key, it, ttl) }
    is CacheResult.Error -> throw result.cause
}
