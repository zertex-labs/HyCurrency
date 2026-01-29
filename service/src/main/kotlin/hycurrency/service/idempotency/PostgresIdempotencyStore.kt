package hycurrency.service.idempotency

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.sql.DataSource
import kotlin.time.Duration

/**
 * PostgreSQL implementation of IdempotencyStore
 */
class PostgresIdempotencyStore(
    private val dataSource: DataSource,
    private val defaultTtl: Duration
) : IdempotencyStore {

    override suspend fun <T> check(
        transactionId: String,
        deserialize: (String) -> T
    ): IdempotencyResult<T> = withContext(Dispatchers.IO) {
        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(
                    "SELECT result_json FROM processed_transactions WHERE transaction_id = ?"
                ).use { stmt ->
                    stmt.setString(1, transactionId)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            val resultJson = rs.getString("result_json")
                            IdempotencyResult.Duplicate(deserialize(resultJson))
                        } else {
                            IdempotencyResult.New
                        }
                    }
                }
            }
        } catch (e: Exception) {
            IdempotencyResult.Error(e)
        }
    }

    override suspend fun <T> store(
        transactionId: String,
        result: T,
        ttl: Duration,
        serialize: (T) -> String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(
                    """
                    INSERT INTO processed_transactions (transaction_id, result_json, created_at)
                    VALUES (?, ?, NOW())
                    ON CONFLICT (transaction_id) DO NOTHING
                    """.trimIndent()
                ).use { stmt ->
                    stmt.setString(1, transactionId)
                    stmt.setString(2, serialize(result))
                    stmt.executeUpdate() > 0
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun cleanup(): Int = withContext(Dispatchers.IO) {
        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(
                    """
                    DELETE FROM processed_transactions
                    WHERE created_at < NOW() - INTERVAL '1 second' * ?
                    """.trimIndent()
                ).use { stmt ->
                    stmt.setLong(1, defaultTtl.inWholeSeconds)
                    stmt.executeUpdate()
                }
            }
        } catch (e: Exception) {
            0
        }
    }
}
