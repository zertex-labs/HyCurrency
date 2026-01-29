package hycurrency.service

import hycurrency.cache.CacheClient
import hycurrency.cache.getOrNull
import hycurrency.proto.*
import hycurrency.service.config.CurrencyConfigProvider
import hycurrency.service.config.ServiceConfig
import hycurrency.service.idempotency.IdempotencyResult
import hycurrency.service.idempotency.IdempotencyStore
import hycurrency.service.repository.CurrencyRepository
import hycurrency.service.repository.RepositoryResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * gRPC service implementation for CurrencyService.
 */
class CurrencyServiceImpl(
    private val config: ServiceConfig,
    private val currencyConfig: CurrencyConfigProvider,
    private val repository: CurrencyRepository,
    private val idempotencyStore: IdempotencyStore,
    private val cacheClient: CacheClient
) : CurrencyServiceGrpcKt.CurrencyServiceCoroutineImplBase() {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getBalance(request: GetBalanceRequest): GetBalanceResponse {
        val playerId = parsePlayerId(request.playerId)
        val currencyType = request.currencyType

        // Check cache first
        val cacheKey = currencyCacheKey(playerId, currencyType)
        val cached = cacheClient.get(cacheKey).getOrNull()
        if (cached != null) {
            val balance = String(cached).toLongOrNull()
            if (balance != null) {
                return getBalanceResponse { this.balance = balance }
            }
        }

        return when (val result = repository.getBalance(playerId, currencyType)) {
            is RepositoryResult.Success -> {
                cacheClient.set(
                    cacheKey,
                    result.data.toString().toByteArray(),
                    config.cache.balanceTtl
                )
                getBalanceResponse { balance = result.data }
            }
            is RepositoryResult.UnknownCurrencyType -> {
                getBalanceResponse {
                    error = errorInfo {
                        code = "INVALID_CURRENCY_TYPE" // TODO add an enum or some form of single truth for error codes
                        message = "Unknown currency type: ${result.currencyType}"
                    }
                }
            }
            is RepositoryResult.NotFound -> {
                // Player not found - return starting balance
                val startingBalance = currencyConfig.get(currencyType)?.startingBalance ?: 0L
                getBalanceResponse { balance = startingBalance }
            }
            is RepositoryResult.InsufficientFunds,
            is RepositoryResult.MaxBalanceExceeded -> {
                // Should not happen for getBalance
                getBalanceResponse {
                    error = errorInfo {
                        code = "INTERNAL_ERROR" // TODO add an enum or some form of single truth for error codes
                        message = "Unexpected result for getBalance"
                    }
                }
            }
            is RepositoryResult.Error -> {
                getBalanceResponse {
                    error = errorInfo {
                        code = "DATABASE_ERROR" // TODO add an enum or some form of single truth for error codes
                        message = result.cause.message ?: "Unknown error"
                    }
                }
            }
        }
    }

    override suspend fun getAllBalances(request: GetAllBalancesRequest): GetAllBalancesResponse {
        val playerId = parsePlayerId(request.playerId)

        // Check cache first
        val cacheKey = allBalancesCacheKey(playerId)
        val cached = cacheClient.get(cacheKey).getOrNull()
        if (cached != null) {
            try {
                val balanceMap = json.decodeFromString<Map<String, Long>>(String(cached))
                return getAllBalancesResponse {
                    balances = balances {
                        entries.addAll(balanceMap.map { (type, amount) ->
                            balance {
                                currencyType = type
                                this.amount = amount
                            }
                        })
                    }
                }
            } catch (_: Exception) {
                // Invalid cache, ignore and query DB
            }
        }

        // Cache miss - query repository
        return when (val result = repository.getAllBalances(playerId)) {
            is RepositoryResult.Success -> {
                // Cache the result
                cacheClient.set(
                    cacheKey,
                    json.encodeToString(result.data).toByteArray(),
                    config.cache.balanceTtl
                )
                getAllBalancesResponse {
                    balances = balances {
                        entries.addAll(result.data.map { (type, amount) ->
                            balance {
                                currencyType = type
                                this.amount = amount
                            }
                        })
                    }
                }
            }
            is RepositoryResult.NotFound,
            is RepositoryResult.UnknownCurrencyType,
            is RepositoryResult.InsufficientFunds,
            is RepositoryResult.MaxBalanceExceeded -> {
                // No balances - return empty
                getAllBalancesResponse {
                    balances = balances { }
                }
            }
            is RepositoryResult.Error -> {
                getAllBalancesResponse {
                    error = errorInfo {
                        code = "DATABASE_ERROR" // TODO add an enum or some form of single truth for error codes
                        message = result.cause.message ?: "Unknown error"
                    }
                }
            }
        }
    }

    override suspend fun deduct(request: DeductRequest): TransactionResponse {
        val transactionId = request.transactionId
        val playerId = parsePlayerId(request.playerId)
        val currencyType = request.currencyType
        val amount = request.amount

        if (amount <= 0) {
            return transactionResponse {
                error = errorInfo {
                    code = "INVALID_AMOUNT" // TODO add an enum or some form of single truth for error codes
                    message = "Amount must be positive"
                }
            }
        }

        when (val idempResult = idempotencyStore.check(transactionId) { json.decodeFromString<CachedTransactionResult>(it) }) {
            is IdempotencyResult.Duplicate -> {
                return idempResult.cachedResult.toTransactionResponse()
            }
            is IdempotencyResult.Error -> {
                return transactionResponse {
                    error = errorInfo {
                        code = "IDEMPOTENCY_ERROR"
                        message = idempResult.cause.message ?: "Unknown error"
                    }
                }
            }
            is IdempotencyResult.New -> {
                // Proceed with the operation
            }
        }

        val response = when (val result = repository.deduct(playerId, currencyType, amount)) {
            is RepositoryResult.Success -> {
                invalidateCache(playerId, currencyType)

                transactionResponse {
                    success = transactionSuccess {
                        this.transactionId = transactionId
                        newBalance = result.data.newBalance
                        remainder = result.data.remainder
                    }
                }
            }
            is RepositoryResult.UnknownCurrencyType -> {
                transactionResponse {
                    error = errorInfo {
                        code = "INVALID_CURRENCY_TYPE"
                        message = "Unknown currency type: ${result.currencyType}"
                    }
                }
            }
            is RepositoryResult.InsufficientFunds -> {
                transactionResponse {
                    insufficientFunds = insufficientFundsError {
                        this.playerId = playerId { uuid = playerId.toString() }
                        this.currencyType = result.currencyType
                        required = result.required
                        available = result.available
                    }
                }
            }
            is RepositoryResult.MaxBalanceExceeded -> {
                // Should not happen for deduct
                transactionResponse {
                    error = errorInfo {
                        code = "INTERNAL_ERROR"
                        message = "Unexpected MaxBalanceExceeded for deduct"
                    }
                }
            }
            is RepositoryResult.NotFound -> {
                transactionResponse {
                    playerNotFound = playerNotFoundError {
                        this.playerId = playerId { uuid = playerId.toString() }
                    }
                }
            }
            is RepositoryResult.Error -> {
                transactionResponse {
                    error = errorInfo {
                        code = "DATABASE_ERROR"
                        message = result.cause.message ?: "Unknown error"
                    }
                }
            }
        }

        val cachedResult = response.toCachedResult()
        idempotencyStore.store(
            transactionId,
            cachedResult,
            config.idempotency.ttl
        ) { json.encodeToString(it) }

        return response
    }

    override suspend fun add(request: AddRequest): TransactionResponse {
        val transactionId = request.transactionId
        val playerId = parsePlayerId(request.playerId)
        val currencyType = request.currencyType
        val amount = request.amount

        if (amount <= 0) {
            return transactionResponse {
                error = errorInfo {
                    code = "INVALID_AMOUNT"
                    message = "Amount must be positive"
                }
            }
        }

        when (val idempResult = idempotencyStore.check(transactionId) { json.decodeFromString<CachedTransactionResult>(it) }) {
            is IdempotencyResult.Duplicate -> {
                return idempResult.cachedResult.toTransactionResponse()
            }
            is IdempotencyResult.Error -> {
                return transactionResponse {
                    error = errorInfo {
                        code = "IDEMPOTENCY_ERROR"
                        message = idempResult.cause.message ?: "Unknown error"
                    }
                }
            }
            is IdempotencyResult.New -> {
                // Proceed with the operation
            }
        }

        val response = when (val result = repository.add(playerId, currencyType, amount)) {
            is RepositoryResult.Success -> {
                invalidateCache(playerId, currencyType)

                transactionResponse {
                    success = transactionSuccess {
                        this.transactionId = transactionId
                        newBalance = result.data.newBalance
                        remainder = result.data.remainder
                    }
                }
            }
            is RepositoryResult.UnknownCurrencyType -> {
                transactionResponse {
                    error = errorInfo {
                        code = "INVALID_CURRENCY_TYPE"
                        message = "Unknown currency type: ${result.currencyType}"
                    }
                }
            }
            is RepositoryResult.MaxBalanceExceeded -> {
                transactionResponse {
                    error = errorInfo {
                        code = "MAX_BALANCE_EXCEEDED"
                        message = "Would exceed max balance of ${result.maxBalance} for ${result.currencyType}"
                    }
                }
            }
            is RepositoryResult.InsufficientFunds -> {
                // Should not happen for add
                transactionResponse {
                    error = errorInfo {
                        code = "INTERNAL_ERROR"
                        message = "Unexpected InsufficientFunds for add"
                    }
                }
            }
            is RepositoryResult.NotFound -> {
                transactionResponse {
                    playerNotFound = playerNotFoundError {
                        this.playerId = playerId { uuid = playerId.toString() }
                    }
                }
            }
            is RepositoryResult.Error -> {
                transactionResponse {
                    error = errorInfo {
                        code = "DATABASE_ERROR"
                        message = result.cause.message ?: "Unknown error"
                    }
                }
            }
        }

        val cachedResult = response.toCachedResult()
        idempotencyStore.store(
            transactionId,
            cachedResult,
            config.idempotency.ttl
        ) { json.encodeToString(it) }

        return response
    }

    override suspend fun transfer(request: TransferRequest): TwoPartyTransactionResponse {
        val transactionId = request.transactionId
        val fromPlayerId = parsePlayerId(request.fromPlayerId)
        val toPlayerId = parsePlayerId(request.toPlayerId)
        val currencyType = request.currencyType
        val amount = request.amount

        val currencyTypeConfig = currencyConfig.get(currencyType)
        if (currencyTypeConfig == null) {
            return twoPartyTransactionResponse {
                error = errorInfo {
                    code = "INVALID_CURRENCY_TYPE"
                    message = "Unknown currency type: $currencyType"
                }
            }
        }

        if (!currencyTypeConfig.transferable) {
            return twoPartyTransactionResponse {
                error = errorInfo {
                    code = "TRANSFER_NOT_ALLOWED"
                    message = "Currency type $currencyType is not transferable"
                }
            }
        }

        if (amount <= 0) {
            return twoPartyTransactionResponse {
                error = errorInfo {
                    code = "INVALID_AMOUNT"
                    message = "Amount must be positive"
                }
            }
        }

        if (fromPlayerId == toPlayerId) {
            return twoPartyTransactionResponse {
                error = errorInfo {
                    code = "SELF_TRANSFER"
                    message = "Cannot transfer to yourself"
                }
            }
        }

        when (val idempResult = idempotencyStore.check(transactionId) { json.decodeFromString<CachedTwoPartyResult>(it) }) {
            is IdempotencyResult.Duplicate -> {
                return idempResult.cachedResult.toTwoPartyTransactionResponse()
            }
            is IdempotencyResult.Error -> {
                return twoPartyTransactionResponse {
                    error = errorInfo {
                        code = "IDEMPOTENCY_ERROR"
                        message = idempResult.cause.message ?: "Unknown error"
                    }
                }
            }
            is IdempotencyResult.New -> {
                // Proceed with the operation
            }
        }

        val response = when (val result = repository.transfer(fromPlayerId, toPlayerId, currencyType, amount)) {
            is RepositoryResult.Success -> {
                invalidateCache(fromPlayerId, currencyType)
                invalidateCache(toPlayerId, currencyType)

                twoPartyTransactionResponse {
                    success = twoPartyTransactionSuccess {
                        this.transactionId = transactionId
                        senderNewBalance = result.data.senderNewBalance
                        receiverNewBalance = result.data.receiverNewBalance
                        remainder = result.data.remainder
                    }
                }
            }
            is RepositoryResult.UnknownCurrencyType -> {
                twoPartyTransactionResponse {
                    error = errorInfo {
                        code = "INVALID_CURRENCY_TYPE"
                        message = "Unknown currency type: ${result.currencyType}"
                    }
                }
            }
            is RepositoryResult.InsufficientFunds -> {
                twoPartyTransactionResponse {
                    insufficientFunds = insufficientFundsError {
                        playerId = playerId { uuid = fromPlayerId.toString() }
                        this.currencyType = result.currencyType
                        required = result.required
                        available = result.available
                    }
                }
            }
            is RepositoryResult.MaxBalanceExceeded -> {
                twoPartyTransactionResponse {
                    error = errorInfo {
                        code = "RECEIVER_MAX_BALANCE_EXCEEDED"
                        message = "Receiver would exceed max balance of ${result.maxBalance} for ${result.currencyType}"
                    }
                }
            }
            is RepositoryResult.NotFound -> {
                twoPartyTransactionResponse {
                    senderNotFound = playerNotFoundError {
                        playerId = playerId { uuid = fromPlayerId.toString() }
                    }
                }
            }
            is RepositoryResult.Error -> {
                twoPartyTransactionResponse {
                    error = errorInfo {
                        code = "DATABASE_ERROR"
                        message = result.cause.message ?: "Unknown error"
                    }
                }
            }
        }

        val cachedResult = response.toCachedTwoPartyResult()
        idempotencyStore.store(
            transactionId,
            cachedResult,
            config.idempotency.ttl
        ) { json.encodeToString(it) }

        return response
    }

    private fun parsePlayerId(playerId: PlayerId): UUID {
        return UUID.fromString(playerId.uuid)
    }

    private suspend fun invalidateCache(playerId: UUID, currencyType: String) {
        // Delete both specific balance and all-balances cache entries for specified playr id
        cacheClient.delete(
            currencyCacheKey(playerId, currencyType),
            allBalancesCacheKey(playerId)
        )
    }

    private fun currencyCacheKey(playerId: UUID, currencyType: String): String =
        "currency:balance:$playerId:$currencyType"

    private fun allBalancesCacheKey(playerId: UUID): String =
        "currency:balances:$playerId"

    @Serializable
    private data class CachedTransactionResult(
        val type: String, // "success", "insufficient_funds", "player_not_found", "error"
        val transactionId: String? = null,
        val newBalance: Long? = null,
        val remainder: Long? = null,
        val playerIdUuid: String? = null,
        val currencyType: String? = null,
        val required: Long? = null,
        val available: Long? = null,
        val errorCode: String? = null,
        val errorMessage: String? = null
    )

    private fun TransactionResponse.toCachedResult(): CachedTransactionResult = when {
        hasSuccess() -> CachedTransactionResult(
            type = "success",
            transactionId = success.transactionId,
            newBalance = success.newBalance,
            remainder = success.remainder
        )
        hasInsufficientFunds() -> CachedTransactionResult(
            type = "insufficient_funds",
            playerIdUuid = insufficientFunds.playerId.uuid,
            currencyType = insufficientFunds.currencyType,
            required = insufficientFunds.required,
            available = insufficientFunds.available
        )
        hasPlayerNotFound() -> CachedTransactionResult(
            type = "player_not_found",
            playerIdUuid = playerNotFound.playerId.uuid
        )
        hasError() -> CachedTransactionResult(
            type = "error",
            errorCode = error.code,
            errorMessage = error.message
        )
        else -> CachedTransactionResult(type = "unknown")
    }

    private fun CachedTransactionResult.toTransactionResponse(): TransactionResponse = when (type) {
        "success" -> transactionResponse {
            success = transactionSuccess {
                this.transactionId = this@toTransactionResponse.transactionId ?: ""
                newBalance = this@toTransactionResponse.newBalance ?: 0L
                remainder = this@toTransactionResponse.remainder ?: 0L
            }
        }
        "insufficient_funds" -> transactionResponse {
            insufficientFunds = insufficientFundsError {
                playerId = playerId { uuid = this@toTransactionResponse.playerIdUuid ?: "" }
                currencyType = this@toTransactionResponse.currencyType ?: ""
                required = this@toTransactionResponse.required ?: 0L
                available = this@toTransactionResponse.available ?: 0L
            }
        }
        "player_not_found" -> transactionResponse {
            playerNotFound = playerNotFoundError {
                playerId = playerId { uuid = this@toTransactionResponse.playerIdUuid ?: "" }
            }
        }
        else -> transactionResponse {
            error = errorInfo {
                code = errorCode ?: "UNKNOWN"
                message = errorMessage ?: "Unknown error"
            }
        }
    }

    @Serializable
    private data class CachedTwoPartyResult(
        val type: String, // "success", "insufficient_funds", "sender_not_found", "receiver_not_found", "error"
        val transactionId: String? = null,
        val senderNewBalance: Long? = null,
        val receiverNewBalance: Long? = null,
        val remainder: Long? = null,
        val playerIdUuid: String? = null,
        val currencyType: String? = null,
        val required: Long? = null,
        val available: Long? = null,
        val errorCode: String? = null,
        val errorMessage: String? = null
    )

    private fun TwoPartyTransactionResponse.toCachedTwoPartyResult(): CachedTwoPartyResult = when {
        hasSuccess() -> CachedTwoPartyResult(
            type = "success",
            transactionId = success.transactionId,
            senderNewBalance = success.senderNewBalance,
            receiverNewBalance = success.receiverNewBalance,
            remainder = success.remainder
        )
        hasInsufficientFunds() -> CachedTwoPartyResult(
            type = "insufficient_funds",
            playerIdUuid = insufficientFunds.playerId.uuid,
            currencyType = insufficientFunds.currencyType,
            required = insufficientFunds.required,
            available = insufficientFunds.available
        )
        hasSenderNotFound() -> CachedTwoPartyResult(
            type = "sender_not_found",
            playerIdUuid = senderNotFound.playerId.uuid
        )
        hasReceiverNotFound() -> CachedTwoPartyResult(
            type = "receiver_not_found",
            playerIdUuid = receiverNotFound.playerId.uuid
        )
        hasError() -> CachedTwoPartyResult(
            type = "error",
            errorCode = error.code,
            errorMessage = error.message
        )
        else -> CachedTwoPartyResult(type = "unknown")
    }

    private fun CachedTwoPartyResult.toTwoPartyTransactionResponse(): TwoPartyTransactionResponse = when (type) {
        "success" -> twoPartyTransactionResponse {
            success = twoPartyTransactionSuccess {
                this.transactionId = this@toTwoPartyTransactionResponse.transactionId ?: ""
                senderNewBalance = this@toTwoPartyTransactionResponse.senderNewBalance ?: 0L
                receiverNewBalance = this@toTwoPartyTransactionResponse.receiverNewBalance ?: 0L
                remainder = this@toTwoPartyTransactionResponse.remainder ?: 0L
            }
        }
        "insufficient_funds" -> twoPartyTransactionResponse {
            insufficientFunds = insufficientFundsError {
                playerId = playerId { uuid = this@toTwoPartyTransactionResponse.playerIdUuid ?: "" }
                currencyType = this@toTwoPartyTransactionResponse.currencyType ?: ""
                required = this@toTwoPartyTransactionResponse.required ?: 0L
                available = this@toTwoPartyTransactionResponse.available ?: 0L
            }
        }
        "sender_not_found" -> twoPartyTransactionResponse {
            senderNotFound = playerNotFoundError {
                playerId = playerId { uuid = this@toTwoPartyTransactionResponse.playerIdUuid ?: "" }
            }
        }
        "receiver_not_found" -> twoPartyTransactionResponse {
            receiverNotFound = receiverNotFoundError {
                playerId = playerId { uuid = this@toTwoPartyTransactionResponse.playerIdUuid ?: "" }
            }
        }
        else -> twoPartyTransactionResponse {
            error = errorInfo {
                code = errorCode ?: "UNKNOWN"
                message = errorMessage ?: "Unknown error"
            }
        }
    }
}
