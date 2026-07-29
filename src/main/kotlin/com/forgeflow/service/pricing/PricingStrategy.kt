package com.forgeflow.service.pricing

import com.forgeflow.domain.PricingStrategyType
import java.math.BigDecimal

interface PricingStrategy {
	val type: PricingStrategyType

	/** Returns the total price for one quote line, quantity already included. */
	fun calculateLineTotal(context: PricingContext): BigDecimal
}
