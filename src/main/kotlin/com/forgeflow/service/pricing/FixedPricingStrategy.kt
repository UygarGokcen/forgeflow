package com.forgeflow.service.pricing

import com.forgeflow.domain.PricingStrategyType
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

/** No discount and no area maths: the line total is just unit price times quantity. */
@Component
class FixedPricingStrategy : PricingStrategy {

	override val type = PricingStrategyType.FIXED

	override fun calculateLineTotal(context: PricingContext): BigDecimal =
		context.baseUnitPrice.multiply(context.quantity).setScale(4, RoundingMode.HALF_UP)
}
