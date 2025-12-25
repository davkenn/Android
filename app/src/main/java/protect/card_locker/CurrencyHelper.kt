package protect.card_locker

import java.util.Currency

/**
 * Centralized currency data for the app.
 * Provides currency lookup by symbol and list of available symbols.
 */
object CurrencyHelper {
    /** Map of currency symbol to Currency object */
    val currencies: Map<String, Currency> by lazy {
        Currency.getAvailableCurrencies().associateBy { it.symbol }
    }

    /** Map of currency code to symbol (e.g., "USD" to "$") */
    val currencySymbols: Map<String, String> by lazy {
        Currency.getAvailableCurrencies().associate { it.currencyCode to it.symbol }
    }

    /** All available currency symbols */
    val symbols: Set<String> get() = currencies.keys

    /** Look up a Currency by its symbol, or null if not found */
    fun fromSymbol(symbol: String): Currency? = currencies[symbol]

    /** Get the symbol for a Currency, with fallback to Currency.symbol */
    fun getSymbol(currency: Currency): String =
        currencySymbols[currency.currencyCode] ?: currency.symbol
}
