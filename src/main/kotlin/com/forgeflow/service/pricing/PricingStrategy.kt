package com.forgeflow.service.pricing

import com.forgeflow.domain.PricingStrategyType
import java.math.BigDecimal

interface PricingStrategy {
	val type: PricingStrategyType

	/** Returns the total price (already multiplied by quantity) for one quote line item. */
	fun calculateLineTotal(context: PricingContext): BigDecimal
}
