package hycurrency.cache

import io.lettuce.core.RedisClient
import io.lettuce.core.SetArgs
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import io.lettuce.core.codec.ByteArrayCodec
import io.lettuce.core.codec.RedisCodec
import io.lettuce.core.codec.StringCodec
import kotlinx.coroutines.future.await
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class ValkeyCacheClient(
    private val redisUri: String = "redis://localhost:6379"
) : CacheClient {

    private val codec: RedisCodec<String, ByteArray> = RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE)
    private val client: RedisClient = RedisClient.create(redisUri)
    private val connection: StatefulRedisConnection<String, ByteArray> = client.connect(codec)
    private val commands: RedisAsyncCommands<String, ByteArray> = connection.async()

    override suspend fun get(key: String): CacheResult<ByteArray> {
        return try {
            val value = commands.get(key).await()
            if (value != null) {
                CacheResult.Hit<ByteArray>(value)
            } else {
                CacheResult.Miss
            }
        } catch (e: Exception) {
            CacheResult.Error(e)
        }
    }

    override suspend fun mget(vararg keys: String): Map<String, CacheResult<ByteArray>> {
        if (keys.isEmpty()) return emptyMap()
        
        return try {
            val results = mutableMapOf<String, CacheResult<ByteArray>>()
            val values = commands.mget(*keys).await()
            
            values.forEach { kv ->
                results[kv.key] = if (kv.hasValue()) {
                    CacheResult.Hit<ByteArray>(kv.value)
                } else {
                    CacheResult.Miss
                }
            }
            
            // Add missing keys as Miss
            keys.forEach { key ->
                if (key !in results) {
                    results[key] = CacheResult.Miss
                }
            }
            
            results.toMap()
        } catch (e: Exception) {
            keys.associateWith { CacheResult.Error(e) }
        }
    }

    override suspend fun set(key: String, value: ByteArray, ttl: Duration?): Boolean {
        return try {
            if (ttl != null) {
                val args = SetArgs.Builder.ex(ttl.inWholeSeconds)
                commands.set(key, value, args).await() == "OK"
            } else {
                commands.set(key, value).await() == "OK"
            }
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun mset(entries: Map<String, ByteArray>, ttl: Duration?): Boolean {
        if (entries.isEmpty()) return true
        
        return try {
            commands.mset(entries).await()
            
            // Set TTL for each key if specified
            if (ttl != null) {
                entries.keys.forEach { key ->
                    commands.expire(key, ttl.inWholeSeconds).await()
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun delete(key: String): Boolean {
        return try {
            commands.del(key).await() == 1L
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun delete(vararg keys: String): Long {
        if (keys.isEmpty()) return 0L
        return try {
            commands.del(*keys).await() ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    override suspend fun exists(key: String): Boolean {
        return try {
            commands.exists(key).await() == 1L
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun expire(key: String, ttl: Duration): Boolean {
        return try {
            commands.expire(key, ttl.inWholeSeconds).await() ?: false
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun ttl(key: String): Duration? {
        return try {
            val ttlSeconds = commands.ttl(key).await()
            when {
                ttlSeconds == null -> null
                ttlSeconds == -2L -> null  // Key doesn't exist
                ttlSeconds == -1L -> null  // Key exists but has no TTL
                else -> ttlSeconds.seconds
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun close() {
        connection.close()
        client.shutdown()
    }
}
