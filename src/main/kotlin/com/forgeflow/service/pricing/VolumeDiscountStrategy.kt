package com.forgeflow.service.pricing

import com.forgeflow.domain.PricingStrategyType
import com.forgeflow.exception.InvalidPricingConfigException
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Applies a flat percentage discount to the unit price once quantity reaches a threshold.
 *
 * Expected [PricingContext.config] keys:
 * - `minQuantity`: quantity at or above which the discount kicks in
 * - `discountPercent`: percentage (0-100) knocked off the unit price once the threshold is met
 */
@Component
class VolumeDiscountStrategy : PricingStrategy {

	override val type = PricingStrategyType.VOLUME_DISCOUNT

	override fun calculateLineTotal(context: PricingContext): BigDecimal {
		val minQuantity = context.config.requiredDecimal("minQuantity")
		val discountPercent = context.config.requiredDecimal("discountPercent")

		if (discountPercent < BigDecimal.ZERO || discountPercent > BigDecimal(100)) {
			throw InvalidPricingConfigException("discountPercent must be between 0 and 100, got $discountPercent")
		}

		val discountFactor = if (context.quantity >= minQuantity) {
			BigDecimal.ONE.subtract(discountPercent.divide(BigDecimal(100)))
		} else {
			BigDecimal.ONE
		}

		return context.baseUnitPrice
			.multiply(discountFactor)
			.multiply(context.quantity)
			.setScale(4, RoundingMode.HALF_UP)
	}
}
