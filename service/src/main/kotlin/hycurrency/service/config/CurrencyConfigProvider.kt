package hycurrency.service.config

/**
 * Provides access to currency type configurations.
 * Injected into services that need to look up currency behavior.
 */
interface CurrencyConfigProvider {
    /**
     * Get configuration for a currency type.
     * @return null if currency type doesn't exist
     */
    fun get(currencyType: String): CurrencyTypeConfig?

    /**
     * Get all configured currency types.
     */
    fun all(): List<CurrencyTypeConfig>

    /**
     * Check if a currency type exists.
     */
    fun exists(currencyType: String): Boolean = get(currencyType) != null
}

/**
 * Simple implementation backed by a map.
 */
class MapCurrencyConfigProvider(
    currencies: List<CurrencyTypeConfig>
) : CurrencyConfigProvider {
    
    private val configMap: Map<String, CurrencyTypeConfig> = 
        currencies.associateBy { it.id }
    
    override fun get(currencyType: String): CurrencyTypeConfig? = 
        configMap[currencyType]
    
    override fun all(): List<CurrencyTypeConfig> = 
        configMap.values.toList()
}
