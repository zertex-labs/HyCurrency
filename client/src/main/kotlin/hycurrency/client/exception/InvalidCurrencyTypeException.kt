package hycurrency.client.exception

/**
 * Thrown when an operation is attempted with a currency type that doesn't exist
 * in the server's configuration.
 */
class InvalidCurrencyTypeException(
    val currencyType: String,
    message: String = "Invalid currency type: $currencyType"
) : IllegalArgumentException(message)
