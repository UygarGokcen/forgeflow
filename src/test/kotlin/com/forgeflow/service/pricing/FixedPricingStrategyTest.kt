package com.forgeflow.service.pricing

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class FixedPricingStrategyTest {

	private val strategy = FixedPricingStrategy()

	@Test
	fun `multiplies unit price by quantity`() {
		val context = PricingContext(
			baseUnitPrice = BigDecimal("12.50"),
			quantity = BigDecimal("3"),
			config = emptyMap(),
		)

		val total = strategy.calculateLineTotal(context)

		assertEquals(BigDecimal("37.5000"), total)
	}

	@Test
	fun `ignores config entirely`() {
		val context = PricingContext(
			baseUnitPrice = BigDecimal("10"),
			quantity = BigDecimal("1"),
			config = mapOf("minQuantity" to 100, "discountPercent" to 50),
		)

		val total = strategy.calculateLineTotal(context)

		assertEquals(BigDecimal("10.0000"), total)
	}
}
