package com.forgeflow.service.pricing

import com.forgeflow.domain.PricingStrategyType
import com.forgeflow.exception.InvalidPricingConfigException
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Prices by area instead of by piece, for example a cut panel sold per square meter.
 *
 * Here `baseUnitPrice` is the price per square meter. The optional `multiplier` config key is an
 * extra factor on top, such as a waste allowance or a surcharge for difficult work. It defaults
 * to 1.
 */
@Component
class AreaBasedPricingStrategy : PricingStrategy {

	override val type = PricingStrategyType.AREA_BASED

	override fun calculateLineTotal(context: PricingContext): BigDecimal {
		val width = context.width
			?: throw InvalidPricingConfigException("Area-based pricing requires a line item width")
		val height = context.height
			?: throw InvalidPricingConfigException("Area-based pricing requires a line item height")

		val multiplier = context.config.optionalDecimal("multiplier", BigDecimal.ONE)

		return context.baseUnitPrice
			.multiply(width)
			.multiply(height)
			.multiply(multiplier)
			.multiply(context.quantity)
			.setScale(4, RoundingMode.HALF_UP)
	}
}
