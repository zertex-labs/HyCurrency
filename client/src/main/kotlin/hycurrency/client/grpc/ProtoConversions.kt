package hycurrency.client.grpc

import hycurrency.client.exception.InvalidCurrencyTypeException
import hycurrency.client.model.CurrencyResult
import hycurrency.client.model.PlayerId
import hycurrency.client.model.TransactionResult
import hycurrency.client.model.TwoPartyTransactionResult
import hycurrency.proto.ErrorInfo
import hycurrency.proto.GetAllBalancesResponse
import hycurrency.proto.GetBalanceResponse
import hycurrency.proto.TransactionResponse
import hycurrency.proto.TwoPartyTransactionResponse
import hycurrency.proto.playerId

// TODO: handle error codes better
private const val INVALID_CURRENCY_TYPE_CODE = "INVALID_CURRENCY_TYPE"

/**
 * Handles error responses
 * @throws InvalidCurrencyTypeException if the error code indicates an invalid currency type
 */
private fun ErrorInfo.handleError(currencyType: String?): CurrencyResult.Error {
    if (code == INVALID_CURRENCY_TYPE_CODE && currencyType != null) {
        throw InvalidCurrencyTypeException(
            currencyType,
            message.ifEmpty { "Invalid currency type: $currencyType" }
        )
    }
    return CurrencyResult.Error(message = message.ifEmpty { "Unknown error: $code" })
}

/**
 * Convert PlayerId to proto PlayerId message.
 */
fun PlayerId.toProto(): hycurrency.proto.PlayerId = playerId {
    uuid = this@toProto.uuid.toString()
}

/**
 * Convert proto PlayerId to our value class.
 */
fun hycurrency.proto.PlayerId.fromProto(): PlayerId = PlayerId.fromString(uuid)

/**
 * Convert GetBalanceResponse to CurrencyResult<Long>.
 *
 * @throws InvalidCurrencyTypeException if the currency type doesn't exist
 */
fun GetBalanceResponse.toCurrencyResult(playerId: PlayerId, currencyType: String): CurrencyResult<Long> {
    return when (resultCase) {
        GetBalanceResponse.ResultCase.BALANCE -> CurrencyResult.Success(balance)
        GetBalanceResponse.ResultCase.PLAYER_NOT_FOUND -> CurrencyResult.PlayerNotFound(playerId)
        GetBalanceResponse.ResultCase.ERROR -> error.handleError(currencyType)
        GetBalanceResponse.ResultCase.RESULT_NOT_SET, null -> CurrencyResult.Error("Response result not set")
    }
}

/**
 * Convert GetAllBalancesResponse to CurrencyResult<Map<String, Long>>.
 */
fun GetAllBalancesResponse.toCurrencyResult(playerId: PlayerId): CurrencyResult<Map<String, Long>> {
    return when (resultCase) {
        GetAllBalancesResponse.ResultCase.BALANCES -> {
            val map = balances.entriesList.associate { it.currencyType to it.amount }
            CurrencyResult.Success(map)
        }
        GetAllBalancesResponse.ResultCase.PLAYER_NOT_FOUND -> CurrencyResult.PlayerNotFound(playerId)
        GetAllBalancesResponse.ResultCase.ERROR -> error.handleError(currencyType = null)
        GetAllBalancesResponse.ResultCase.RESULT_NOT_SET, null -> CurrencyResult.Error("Response result not set")
    }
}

/**
 * Convert TransactionResponse to CurrencyResult<TransactionResult>.
 *
 * @throws InvalidCurrencyTypeException if the currency type doesn't exist
 */
fun TransactionResponse.toCurrencyResult(
    playerId: PlayerId,
    currencyType: String
): CurrencyResult<TransactionResult> {
    return when (resultCase) {
        TransactionResponse.ResultCase.SUCCESS -> CurrencyResult.Success(
            TransactionResult(
                transactionId = success.transactionId,
                newBalance = success.newBalance,
                remainder = success.remainder
            )
        )
        TransactionResponse.ResultCase.INSUFFICIENT_FUNDS -> CurrencyResult.InsufficientFunds(
            playerId = playerId,
            currencyType = currencyType,
            required = insufficientFunds.required,
            available = insufficientFunds.available
        )
        TransactionResponse.ResultCase.PLAYER_NOT_FOUND -> CurrencyResult.PlayerNotFound(playerId)
        TransactionResponse.ResultCase.ERROR -> error.handleError(currencyType)
        TransactionResponse.ResultCase.RESULT_NOT_SET, null -> CurrencyResult.Error("Response result not set")
    }
}

/**
 * Convert TwoPartyTransactionResponse to CurrencyResult<TwoPartyTransactionResult>.
 *
 * @throws InvalidCurrencyTypeException if the currency type doesn't exist
 */
fun TwoPartyTransactionResponse.toCurrencyResult(
    senderPlayerId: PlayerId,
    receiverPlayerId: PlayerId,
    currencyType: String
): CurrencyResult<TwoPartyTransactionResult> {
    return when (resultCase) {
        TwoPartyTransactionResponse.ResultCase.SUCCESS -> CurrencyResult.Success(
            TwoPartyTransactionResult(
                transactionId = success.transactionId,
                senderNewBalance = success.senderNewBalance,
                receiverNewBalance = success.receiverNewBalance,
                remainder = success.remainder
            )
        )
        TwoPartyTransactionResponse.ResultCase.INSUFFICIENT_FUNDS -> CurrencyResult.InsufficientFunds(
            playerId = senderPlayerId,
            currencyType = currencyType,
            required = insufficientFunds.required,
            available = insufficientFunds.available
        )
        TwoPartyTransactionResponse.ResultCase.SENDER_NOT_FOUND -> CurrencyResult.PlayerNotFound(senderPlayerId)
        TwoPartyTransactionResponse.ResultCase.RECEIVER_NOT_FOUND -> CurrencyResult.ReceiverNotFound(receiverPlayerId)
        TwoPartyTransactionResponse.ResultCase.ERROR -> error.handleError(currencyType)
        TwoPartyTransactionResponse.ResultCase.RESULT_NOT_SET, null -> CurrencyResult.Error("Response result not set")
    }
}
