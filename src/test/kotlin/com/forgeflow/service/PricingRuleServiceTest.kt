package com.forgeflow.service

import com.forgeflow.domain.PricingRule
import com.forgeflow.domain.PricingStrategyType
import com.forgeflow.domain.Product
import com.forgeflow.domain.UnitOfMeasure
import com.forgeflow.dto.CreatePricingRuleRequest
import com.forgeflow.exception.InvalidPricingConfigException
import com.forgeflow.exception.ResourceNotFoundException
import com.forgeflow.repository.PricingRuleRepository
import com.forgeflow.repository.ProductRepository
import com.forgeflow.service.pricing.AreaBasedPricingStrategy
import com.forgeflow.service.pricing.FixedPricingStrategy
import com.forgeflow.service.pricing.PricingStrategyResolver
import com.forgeflow.service.pricing.VolumeDiscountStrategy
import com.forgeflow.support.TenantContextTestSupport
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.UUID

class PricingRuleServiceTest {

	private val pricingRuleRepository: PricingRuleRepository = mock()
	private val productRepository: ProductRepository = mock()
	private val strategyResolver = PricingStrategyResolver(
		listOf(FixedPricingStrategy(), VolumeDiscountStrategy(), AreaBasedPricingStrategy()),
	)
	private val pricingRuleService = PricingRuleService(pricingRuleRepository, productRepository, strategyResolver)

	private val tenantId: UUID = UUID.randomUUID()
	private val product = Product(
		tenantId = tenantId,
		sku = "PANEL-01",
		name = "Panel",
		baseUnitPrice = BigDecimal("20.00"),
		unitOfMeasure = UnitOfMeasure.SQUARE_METER,
		id = UUID.randomUUID(),
	)

	@BeforeEach
	fun bindRequestScope() = TenantContextTestSupport.bind(tenantId)

	@AfterEach
	fun unbindRequestScope() = TenantContextTestSupport.unbind()

	@Test
	fun `create throws when the product does not belong to the tenant`() {
		whenever(productRepository.findByTenantIdAndId(tenantId, product.id!!)).thenReturn(null)

		assertThrows(ResourceNotFoundException::class.java) {
			pricingRuleService.create(
				product.id!!,
				CreatePricingRuleRequest(PricingStrategyType.FIXED, emptyMap(), 0),
			)
		}
	}

	@Test
	fun `create rejects a config that the real strategy can't process`() {
		whenever(productRepository.findByTenantIdAndId(tenantId, product.id!!)).thenReturn(product)

		assertThrows(InvalidPricingConfigException::class.java) {
			pricingRuleService.create(
				product.id!!,
				CreatePricingRuleRequest(
					PricingStrategyType.VOLUME_DISCOUNT,
					mapOf("minQuantity" to 10),
					0,
				),
			)
		}
	}

	@Test
	fun `create persists a rule once the config validates against the real strategy`() {
		whenever(productRepository.findByTenantIdAndId(tenantId, product.id!!)).thenReturn(product)
		doAnswer { (it.arguments[0] as PricingRule).also { r -> r.id = UUID.randomUUID() } }
			.whenever(pricingRuleRepository).save(any())

		val response = pricingRuleService.create(
			product.id!!,
			CreatePricingRuleRequest(PricingStrategyType.AREA_BASED, mapOf("multiplier" to 1.1), 5),
		)

		assertEquals(PricingStrategyType.AREA_BASED, response.strategyType)
		assertEquals(5, response.priority)
	}
}
