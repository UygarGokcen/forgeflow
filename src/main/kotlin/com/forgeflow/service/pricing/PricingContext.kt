package com.forgeflow.service.pricing

import com.forgeflow.exception.InvalidPricingConfigException
import java.math.BigDecimal

/**
 * Everything a [PricingStrategy] needs to price one quote line item. `config` is the
 * [com.forgeflow.domain.PricingRule.config] JSONB blob for the rule being applied.
 */
data class PricingContext(
	val baseUnitPrice: BigDecimal,
	val quantity: BigDecimal,
	val width: BigDecimal? = null,
	val height: BigDecimal? = null,
	val config: Map<String, Any>,
)

fun Map<String, Any>.requiredDecimal(key: String): BigDecimal {
	val value = this[key] ?: throw InvalidPricingConfigException("Missing required pricing config key '$key'")
	return when (value) {
		is BigDecimal -> value
		is Number -> BigDecimal(value.toString())
		is String -> value.toBigDecimalOrNull()
			?: throw InvalidPricingConfigException("Pricing config key '$key' is not a valid number: '$value'")
		else -> throw InvalidPricingConfigException("Pricing config key '$key' must be numeric, got ${value::class.simpleName}")
	}
}

fun Map<String, Any>.optionalDecimal(key: String, default: BigDecimal): BigDecimal =
	if (containsKey(key)) requiredDecimal(key) else default
