package com.forgeflow.service.pricing

import com.forgeflow.exception.InvalidPricingConfigException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class VolumeDiscountStrategyTest {

	private val strategy = VolumeDiscountStrategy()

	@Test
	fun `charges full price below the quantity threshold`() {
		val context = PricingContext(
			baseUnitPrice = BigDecimal("10.00"),
			quantity = BigDecimal("5"),
			config = mapOf("minQuantity" to 10, "discountPercent" to 20),
		)

		val total = strategy.calculateLineTotal(context)

		assertEquals(BigDecimal("50.0000"), total)
	}

	@Test
	fun `applies discount once quantity reaches the threshold`() {
		val context = PricingContext(
			baseUnitPrice = BigDecimal("10.00"),
			quantity = BigDecimal("10"),
			config = mapOf("minQuantity" to 10, "discountPercent" to 20),
		)

		val total = strategy.calculateLineTotal(context)

		// unit price drops to 8.00 (20% off), 10 units -> 80.00
		assertEquals(BigDecimal("80.0000"), total)
	}

	@Test
	fun `applies discount above the threshold too`() {
		val context = PricingContext(
			baseUnitPrice = BigDecimal("10.00"),
			quantity = BigDecimal("100"),
			config = mapOf("minQuantity" to 10, "discountPercent" to 15),
		)

		val total = strategy.calculateLineTotal(context)

		assertEquals(BigDecimal("850.0000"), total)
	}

	@Test
	fun `rejects a discount percent outside 0-100`() {
		val context = PricingContext(
			baseUnitPrice = BigDecimal("10.00"),
			quantity = BigDecimal("100"),
			config = mapOf("minQuantity" to 10, "discountPercent" to 150),
		)

		assertThrows(InvalidPricingConfigException::class.java) {
			strategy.calculateLineTotal(context)
		}
	}

	@Test
	fun `rejects a missing config key`() {
		val context = PricingContext(
			baseUnitPrice = BigDecimal("10.00"),
			quantity = BigDecimal("100"),
			config = mapOf("minQuantity" to 10),
		)

		assertThrows(InvalidPricingConfigException::class.java) {
			strategy.calculateLineTotal(context)
		}
	}
}
