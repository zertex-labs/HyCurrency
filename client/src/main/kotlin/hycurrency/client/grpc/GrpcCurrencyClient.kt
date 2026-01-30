package hycurrency.client.grpc

import hycurrency.client.CurrencyClient
import hycurrency.client.model.CurrencyResult
import hycurrency.client.model.PlayerId
import hycurrency.client.model.TransactionResult
import hycurrency.client.model.TwoPartyTransactionResult
import hycurrency.proto.CurrencyServiceGrpcKt
import hycurrency.proto.addRequest
import hycurrency.proto.deductRequest
import hycurrency.proto.getAllBalancesRequest
import hycurrency.proto.getBalanceRequest
import hycurrency.proto.transferRequest
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import java.util.concurrent.TimeUnit

/**
 * gRPC implementation of [hycurrency.client.CurrencyClient].
 */
class GrpcCurrencyClient(
    private val channel: ManagedChannel
) : CurrencyClient {
    private val stub = CurrencyServiceGrpcKt.CurrencyServiceCoroutineStub(channel)

    companion object {
        fun create(host: String, port: Int): GrpcCurrencyClient {
            val channel = ManagedChannelBuilder
                .forAddress(host, port)
                .usePlaintext() // TODO: development hack (use TLS in production)
                .build()
            return GrpcCurrencyClient(channel)
        }
    }

    override suspend fun getBalance(playerId: PlayerId, currencyType: String): CurrencyResult<Long> {
        val request = getBalanceRequest {
            this.playerId = playerId.toProto()
            this.currencyType = currencyType
        }
        return stub.getBalance(request).toCurrencyResult(playerId, currencyType)
    }

    override suspend fun getAllBalances(playerId: PlayerId): CurrencyResult<Map<String, Long>> {
        val request = getAllBalancesRequest {
            this.playerId = playerId.toProto()
        }

        return stub.getAllBalances(request).toCurrencyResult(playerId)
    }

    override suspend fun deduct(
        transactionId: String,
        playerId: PlayerId,
        currencyType: String,
        amount: Long,
        reason: String?
    ): CurrencyResult<TransactionResult> {
         val request = deductRequest {
             this.transactionId = transactionId
             this.playerId = playerId.toProto()
             this.currencyType = currencyType
             this.amount = amount
             reason?.let { this.reason = it }
         }
         return stub.deduct(request).toCurrencyResult(playerId, currencyType)
    }

    override suspend fun add(
        transactionId: String,
        playerId: PlayerId,
        currencyType: String,
        amount: Long,
        reason: String?
    ): CurrencyResult<TransactionResult> {
        val request = addRequest {
            this.transactionId = transactionId
            this.playerId = playerId.toProto()
            this.currencyType = currencyType
            this.amount = amount
            reason?.let { this.reason = it }
        }

        return stub.add(request).toCurrencyResult(playerId, currencyType)
    }

    override suspend fun transfer(
        transactionId: String,
        fromPlayerId: PlayerId,
        toPlayerId: PlayerId,
        currencyType: String,
        amount: Long,
        reason: String?
    ): CurrencyResult<TwoPartyTransactionResult> {
        val request = transferRequest {
            this.transactionId = transactionId
            this.fromPlayerId = fromPlayerId.toProto()
            this.toPlayerId = toPlayerId.toProto()
            this.currencyType = currencyType
            this.amount = amount
            reason?.let { this.reason = it }
        }

        return stub.transfer(request).toCurrencyResult(fromPlayerId, toPlayerId, currencyType)
    }

    override fun close() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
}