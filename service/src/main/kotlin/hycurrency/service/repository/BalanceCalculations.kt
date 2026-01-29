package hycurrency.service.repository

import hycurrency.service.config.CurrencyTypeConfig
import hycurrency.service.config.MaxBalanceBehaviour
import hycurrency.service.config.NegativeBehaviour

/**
 * Result of calculating how much to add to a balance.
 */
sealed interface AddCalculation {
    /** Add the full amount, no clamping needed */
    data class Full(val amount: Long) : AddCalculation
    
    /** Add a partial amount due to max_balance clamping */
    data class Clamped(val actualAmount: Long, val remainder: Long) : AddCalculation
    
    /** Rejected due to STRICT max_balance behaviour */
    data class Rejected(
        val currencyType: String,
        val maxBalance: Long,
        val currentBalance: Long,
        val attemptedAdd: Long
    ) : AddCalculation
}

/**
 * Result of calculating how much to deduct from a balance.
 */
sealed interface DeductCalculation {
    /** Deduct the full amount */
    data class Full(val amount: Long) : DeductCalculation
    
    /** Deduct a partial amount due to negative_balance clamping */
    data class Clamped(val actualAmount: Long, val remainder: Long) : DeductCalculation
    
    /** Rejected due to STRICT negative_behaviour (insufficient funds) */
    data class Rejected(
        val currencyType: String,
        val required: Long,
        val available: Long
    ) : DeductCalculation
}

/**
 * Helper functions for calculating balance mutations.
 */
object BalanceCalculations {

    /**
     * Calculate how much can be added to a balance given the currency config.
     *
     * @param currentBalance The player's current balance
     * @param amount The amount requested to add
     * @param config The currency configuration
     * @return AddCalculation indicating full, clamped, or rejected
     */
    fun calculateAdd(
        currentBalance: Long,
        amount: Long,
        config: CurrencyTypeConfig
    ): AddCalculation {
        // No max balance limit - add full amount
        if (!config.enforcesMaxBalance) {
            return AddCalculation.Full(amount)
        }

        val potentialBalance = currentBalance + amount

        // Fits within limit - add full amount
        if (potentialBalance <= config.maxBalance) {
            return AddCalculation.Full(amount)
        }

        // Would exceed limit - check behaviour
        return when (config.maxBalanceBehaviour) {
            MaxBalanceBehaviour.STRICT -> AddCalculation.Rejected(
                currencyType = config.id,
                maxBalance = config.maxBalance,
                currentBalance = currentBalance,
                attemptedAdd = amount
            )
            MaxBalanceBehaviour.CLAMP -> {
                val canAdd = (config.maxBalance - currentBalance).coerceAtLeast(0L)
                val remainder = amount - canAdd
                AddCalculation.Clamped(actualAmount = canAdd, remainder = remainder)
            }
        }
    }

    /**
     * Calculate how much can be deducted from a balance given the currency config.
     *
     * @param currentBalance The player's current balance
     * @param amount The amount requested to deduct
     * @param config The currency configuration
     * @return DeductCalculation indicating full, clamped, or rejected
     */
    fun calculateDeduct(
        currentBalance: Long,
        amount: Long,
        config: CurrencyTypeConfig
    ): DeductCalculation {
        // Has enough balance - deduct full amount
        if (currentBalance >= amount) {
            return DeductCalculation.Full(amount)
        }

        // Insufficient balance - check behaviour
        return when (config.negativeBehaviour) {
            NegativeBehaviour.ALLOW -> {
                // Allow negative balance - deduct full amount
                DeductCalculation.Full(amount)
            }
            NegativeBehaviour.CLAMP -> {
                // Deduct only what's available
                val canDeduct = currentBalance.coerceAtLeast(0L)
                val remainder = amount - canDeduct
                DeductCalculation.Clamped(actualAmount = canDeduct, remainder = remainder)
            }
            NegativeBehaviour.STRICT -> {
                // Reject the operation
                DeductCalculation.Rejected(
                    currencyType = config.id,
                    required = amount,
                    available = currentBalance
                )
            }
        }
    }
}
