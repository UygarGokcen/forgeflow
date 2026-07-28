package com.forgeflow.service.pricing

import com.forgeflow.exception.InvalidPricingConfigException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class AreaBasedPricingStrategyTest {

	private val strategy = AreaBasedPricingStrategy()

	@Test
	fun `prices by width times height times unit price`() {
		val context = PricingContext(
			baseUnitPrice = BigDecimal("20.00"),
			quantity = BigDecimal("1"),
			width = BigDecimal("2.0"),
			height = BigDecimal("1.5"),
			config = emptyMap(),
		)

		val total = strategy.calculateLineTotal(context)

		// 2.0 * 1.5 * 20.00 = 60.00
		assertEquals(BigDecimal("60.0000"), total)
	}

	@Test
	fun `applies the optional multiplier`() {
		val context = PricingContext(
			baseUnitPrice = BigDecimal("20.00"),
			quantity = BigDecimal("2"),
			width = BigDecimal("2.0"),
			height = BigDecimal("1.5"),
			config = mapOf("multiplier" to 1.1),
		)

		val total = strategy.calculateLineTotal(context)

		// 2.0 * 1.5 * 20.00 * 1.1 * 2 = 132.00
		assertEquals(BigDecimal("132.0000"), total)
	}

	@Test
	fun `rejects a missing width`() {
		val context = PricingContext(
			baseUnitPrice = BigDecimal("20.00"),
			quantity = BigDecimal("1"),
			height = BigDecimal("1.5"),
			config = emptyMap(),
		)

		assertThrows(InvalidPricingConfigException::class.java) {
			strategy.calculateLineTotal(context)
		}
	}

	@Test
	fun `rejects a missing height`() {
		val context = PricingContext(
			baseUnitPrice = BigDecimal("20.00"),
			quantity = BigDecimal("1"),
			width = BigDecimal("2.0"),
			config = emptyMap(),
		)

		assertThrows(InvalidPricingConfigException::class.java) {
			strategy.calculateLineTotal(context)
		}
	}
}
