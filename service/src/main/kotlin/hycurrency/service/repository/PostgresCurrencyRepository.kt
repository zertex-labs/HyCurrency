package hycurrency.service.repository

import hycurrency.service.config.CurrencyConfigProvider
import hycurrency.service.config.CurrencyTypeConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.util.UUID
import javax.sql.DataSource

/**
 * PostgreSQL implementation of CurrencyRepository.
 */
class PostgresCurrencyRepository(
    private val dataSource: DataSource,
    private val configProvider: CurrencyConfigProvider
) : CurrencyRepository {

    // Background scope for fire-and-forget operations (e.g., inserting missing records)
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun getConfig(currencyType: String): CurrencyTypeConfig? =
        configProvider.get(currencyType)

    override suspend fun getBalance(
        playerId: UUID,
        currencyType: String
    ): RepositoryResult<Long> = withContext(Dispatchers.IO) {
        val config = getConfig(currencyType)
            ?: return@withContext RepositoryResult.UnknownCurrencyType(currencyType)

        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(
                    "SELECT amount FROM balances WHERE player_id = ? AND currency_type = ?"
                ).use { stmt ->
                    stmt.setObject(1, playerId)
                    stmt.setString(2, currencyType)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            RepositoryResult.Success(rs.getLong("amount"))
                        } else {
                            // Player has no record - insert in background and return starting balance
                            insertStartingBalanceInBackground(playerId, currencyType, config.startingBalance)
                            RepositoryResult.Success(config.startingBalance)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            RepositoryResult.Error(e)
        }
    }

    /**
     * Insert starting balance record in background (Uses `INSERT ON CONFLICT DO NOTHING` to hopefully handle race conditions.)
     */
    private fun insertStartingBalanceInBackground(
        playerId: UUID,
        currencyType: String,
        startingBalance: Long
    ) {
        backgroundScope.launch {
            try {
                dataSource.connection.use { conn ->
                    conn.prepareStatement(
                        """
                        INSERT INTO balances (player_id, currency_type, amount, updated_at)
                        VALUES (?, ?, ?, NOW())
                        ON CONFLICT (player_id, currency_type) DO NOTHING
                        """.trimIndent()
                    ).use { stmt ->
                        stmt.setObject(1, playerId)
                        stmt.setString(2, currencyType)
                        stmt.setLong(3, startingBalance)
                        stmt.executeUpdate()
                    }
                }
            } catch (_: Exception) { }
        }
    }

    override suspend fun getAllBalances(playerId: UUID): RepositoryResult<Map<String, Long>> =
        withContext(Dispatchers.IO) {
            try {
                dataSource.connection.use { conn ->
                    conn.prepareStatement(
                        "SELECT currency_type, amount FROM balances WHERE player_id = ?"
                    ).use { stmt ->
                        stmt.setObject(1, playerId)
                        stmt.executeQuery().use { rs ->
                            val balances = mutableMapOf<String, Long>()
                            while (rs.next()) {
                                balances[rs.getString("currency_type")] = rs.getLong("amount")
                            }
                            RepositoryResult.Success(balances)
                        }
                    }
                }
            } catch (e: Exception) {
                RepositoryResult.Error(e)
            }
        }

    override suspend fun add(
        playerId: UUID,
        currencyType: String,
        amount: Long
    ): RepositoryResult<MutationResult> = withContext(Dispatchers.IO) {
        val config = getConfig(currencyType)
            ?: return@withContext RepositoryResult.UnknownCurrencyType(currencyType)

        try {
            dataSource.connection.use { conn ->
                val currentBalance = getCurrentBalance(conn, playerId, currencyType)
                    ?: config.startingBalance

                val calculation = BalanceCalculations.calculateAdd(currentBalance, amount, config)

                when (calculation) {
                    is AddCalculation.Rejected -> {
                        RepositoryResult.MaxBalanceExceeded(
                            currencyType = calculation.currencyType,
                            maxBalance = calculation.maxBalance,
                            currentBalance = calculation.currentBalance,
                            attemptedAdd = calculation.attemptedAdd
                        )
                    }
                    is AddCalculation.Full -> {
                        val newBalance = upsertBalance(conn, playerId, currencyType, calculation.amount, config.startingBalance)
                        RepositoryResult.Success(MutationResult(newBalance, remainder = 0L))
                    }
                    is AddCalculation.Clamped -> {
                        val newBalance = upsertBalance(conn, playerId, currencyType, calculation.actualAmount, config.startingBalance)
                        RepositoryResult.Success(MutationResult(newBalance, remainder = calculation.remainder))
                    }
                }
            }
        } catch (e: Exception) {
            RepositoryResult.Error(e)
        }
    }

    override suspend fun deduct(
        playerId: UUID,
        currencyType: String,
        amount: Long
    ): RepositoryResult<MutationResult> = withContext(Dispatchers.IO) {
        val config = getConfig(currencyType)
            ?: return@withContext RepositoryResult.UnknownCurrencyType(currencyType)

        try {
            dataSource.connection.use { conn ->
                val currentBalance = getCurrentBalance(conn, playerId, currencyType)
                    ?: config.startingBalance

                val calculation = BalanceCalculations.calculateDeduct(currentBalance, amount, config)

                when (calculation) {
                    is DeductCalculation.Rejected -> {
                        RepositoryResult.InsufficientFunds(
                            currencyType = calculation.currencyType,
                            required = calculation.required,
                            available = calculation.available
                        )
                    }
                    is DeductCalculation.Full -> {
                        val newBalance = upsertBalance(conn, playerId, currencyType, -calculation.amount, config.startingBalance)
                        RepositoryResult.Success(MutationResult(newBalance, remainder = 0L))
                    }
                    is DeductCalculation.Clamped -> {
                        val newBalance = upsertBalance(conn, playerId, currencyType, -calculation.actualAmount, config.startingBalance)
                        RepositoryResult.Success(MutationResult(newBalance, remainder = calculation.remainder))
                    }
                }
            }
        } catch (e: Exception) {
            RepositoryResult.Error(e)
        }
    }

    override suspend fun transfer(
        fromPlayerId: UUID,
        toPlayerId: UUID,
        currencyType: String,
        amount: Long
    ): RepositoryResult<TransferMutationResult> = withContext(Dispatchers.IO) {
        val config = getConfig(currencyType)
            ?: return@withContext RepositoryResult.UnknownCurrencyType(currencyType)

        dataSource.connection.use { conn ->
            try {
                conn.autoCommit = false

                val senderBalance = getCurrentBalance(conn, fromPlayerId, currencyType)
                    ?: config.startingBalance
                val receiverBalance = getCurrentBalance(conn, toPlayerId, currencyType)
                    ?: config.startingBalance

                // calculate how much sender can send
                val deductCalc = BalanceCalculations.calculateDeduct(senderBalance, amount, config)
                val maxSenderCanSend = when (deductCalc) {
                    is DeductCalculation.Rejected -> {
                        conn.rollback()
                        return@withContext RepositoryResult.InsufficientFunds(
                            currencyType = deductCalc.currencyType,
                            required = deductCalc.required,
                            available = deductCalc.available
                        )
                    }
                    is DeductCalculation.Full -> deductCalc.amount
                    is DeductCalculation.Clamped -> deductCalc.actualAmount
                }

                // calculate how much receiver can receive
                val addCalc = BalanceCalculations.calculateAdd(receiverBalance, maxSenderCanSend, config)
                val maxReceiverCanReceive = when (addCalc) {
                    is AddCalculation.Rejected -> {
                        conn.rollback()
                        return@withContext RepositoryResult.MaxBalanceExceeded(
                            currencyType = addCalc.currencyType,
                            maxBalance = addCalc.maxBalance,
                            currentBalance = addCalc.currentBalance,
                            attemptedAdd = addCalc.attemptedAdd
                        )
                    }
                    is AddCalculation.Full -> addCalc.amount
                    is AddCalculation.Clamped -> addCalc.actualAmount
                }

                val actualTransferAmount = minOf(maxSenderCanSend, maxReceiverCanReceive)
                val remainder = amount - actualTransferAmount

                if (actualTransferAmount == 0L) {
                    conn.commit()
                    return@withContext RepositoryResult.Success(
                        TransferMutationResult(
                            senderNewBalance = senderBalance,
                            receiverNewBalance = receiverBalance,
                            remainder = remainder
                        )
                    )
                }

                val senderNewBalance = upsertBalance(conn, fromPlayerId, currencyType, -actualTransferAmount, config.startingBalance)
                val receiverNewBalance = upsertBalance(conn, toPlayerId, currencyType, actualTransferAmount, config.startingBalance)

                conn.commit()
                RepositoryResult.Success(
                    TransferMutationResult(
                        senderNewBalance = senderNewBalance,
                        receiverNewBalance = receiverNewBalance,
                        remainder = remainder
                    )
                )
            } catch (e: Exception) {
                conn.rollback()
                RepositoryResult.Error(e)
            } finally {
                conn.autoCommit = true
            }
        }
    }

    override suspend fun setBalance(
        playerId: UUID,
        currencyType: String,
        amount: Long
    ): RepositoryResult<Long> = withContext(Dispatchers.IO) {
        if (getConfig(currencyType) == null)
            return@withContext RepositoryResult.UnknownCurrencyType(currencyType)

        try {
            dataSource.connection.use { conn ->
                conn.prepareStatement(
                    """
                    INSERT INTO balances (player_id, currency_type, amount, updated_at)
                    VALUES (?, ?, ?, NOW())
                    ON CONFLICT (player_id, currency_type)
                    DO UPDATE SET amount = EXCLUDED.amount, updated_at = NOW()
                    RETURNING amount
                    """.trimIndent()
                ).use { stmt ->
                    stmt.setObject(1, playerId)
                    stmt.setString(2, currencyType)
                    stmt.setLong(3, amount)
                    stmt.executeQuery().use { rs ->
                        rs.next()
                        RepositoryResult.Success(rs.getLong("amount"))
                    }
                }
            }
        } catch (e: Exception) {
            RepositoryResult.Error(e)
        }
    }

    private fun getCurrentBalance(conn: Connection, playerId: UUID, currencyType: String): Long? {
        conn.prepareStatement(
            "SELECT amount FROM balances WHERE player_id = ? AND currency_type = ?"
        ).use { stmt ->
            stmt.setObject(1, playerId)
            stmt.setString(2, currencyType)
            stmt.executeQuery().use { rs ->
                return if (rs.next()) rs.getLong("amount") else null
            }
        }
    }

    /**
     * Upsert balance by adding delta to current balance.
     * If no record exists, initializes with startingBalance + delta.
     */
    private fun upsertBalance(
        conn: Connection,
        playerId: UUID,
        currencyType: String,
        delta: Long,
        startingBalance: Long
    ): Long {
        conn.prepareStatement(
            """
            INSERT INTO balances (player_id, currency_type, amount, updated_at)
            VALUES (?, ?, ?, NOW())
            ON CONFLICT (player_id, currency_type)
            DO UPDATE SET amount = balances.amount + EXCLUDED.amount - ?, updated_at = NOW()
            RETURNING amount
            """.trimIndent()
        ).use { stmt ->
            // for insert: amount = startingBalance + delta
            // for update: amount = current + (startingBalance + delta) - startingBalance = current + delta
            stmt.setObject(1, playerId)
            stmt.setString(2, currencyType)
            stmt.setLong(3, startingBalance + delta)
            stmt.setLong(4, startingBalance)
            stmt.executeQuery().use { rs ->
                rs.next()
                return rs.getLong("amount")
            }
        }
    }
}
