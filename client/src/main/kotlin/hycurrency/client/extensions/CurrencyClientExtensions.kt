package hycurrency.client.extensions

import hycurrency.client.CurrencyClient
import hycurrency.client.model.CurrencyResult
import hycurrency.client.model.PlayerId
import hycurrency.client.model.map

/**
 * Check if a player has enough of a specific currency.
 * Returns false if player not found or on error.
 */
suspend fun CurrencyClient.hasEnough(
    playerId: PlayerId,
    currencyType: String,
    amount: Long
): Boolean {
    val result = getBalance(playerId, currencyType)
    return result is CurrencyResult.Success && result.data >= amount
}

/**
 * Check if a player has enough of a specific currency, with detailed result
 */
suspend fun CurrencyClient.checkBalance(
    playerId: PlayerId,
    currencyType: String,
    requiredAmount: Long
): CurrencyResult<Boolean> {
    return getBalance(playerId, currencyType).map { balance ->
        balance >= requiredAmount
    }
}
