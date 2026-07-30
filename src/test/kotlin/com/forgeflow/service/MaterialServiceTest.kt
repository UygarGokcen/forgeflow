package com.forgeflow.service

import com.forgeflow.domain.Material
import com.forgeflow.domain.StockMovement
import com.forgeflow.domain.StockMovementReason
import com.forgeflow.domain.UnitOfMeasure
import com.forgeflow.dto.AdjustStockRequest
import com.forgeflow.dto.CreateMaterialRequest
import com.forgeflow.exception.InvalidStockAdjustmentException
import com.forgeflow.repository.MaterialRepository
import com.forgeflow.repository.StockMovementRepository
import com.forgeflow.support.TenantContextTestSupport
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.UUID

class MaterialServiceTest {

	private val materialRepository: MaterialRepository = mock()
	private val stockMovementRepository: StockMovementRepository = mock()

	private val materialService = MaterialService(materialRepository, stockMovementRepository)

	private val tenantId: UUID = UUID.randomUUID()

	@BeforeEach
	fun bindRequestScope() = TenantContextTestSupport.bind(tenantId)

	@AfterEach
	fun unbindRequestScope() = TenantContextTestSupport.unbind()

	private fun material(stock: BigDecimal) = Material(
		tenantId = tenantId,
		sku = "STEEL-01",
		name = "Steel sheet",
		unitOfMeasure = UnitOfMeasure.KILOGRAM,
		stockQuantity = stock,
		id = UUID.randomUUID(),
	)

	@Test
	fun `creating a material with starting stock records an INITIAL_STOCK movement`() {
		val request = CreateMaterialRequest(
			sku = "STEEL-01",
			name = "Steel sheet",
			unitOfMeasure = UnitOfMeasure.KILOGRAM,
			stockQuantity = BigDecimal("50"),
			reorderLevel = BigDecimal("10"),
		)
		doAnswer { (it.arguments[0] as Material).also { m -> m.id = UUID.randomUUID() } }
			.whenever(materialRepository).save(any())

		materialService.create(request)

		val captor = argumentCaptor<StockMovement>()
		verify(stockMovementRepository).save(captor.capture())
		assertEquals(StockMovementReason.INITIAL_STOCK, captor.firstValue.reason)
		assertEquals(0, BigDecimal("50").compareTo(captor.firstValue.quantityDelta))
	}

	@Test
	fun `creating a material with zero starting stock records no movement`() {
		val request = CreateMaterialRequest(
			sku = "STEEL-01",
			name = "Steel sheet",
			unitOfMeasure = UnitOfMeasure.KILOGRAM,
			stockQuantity = BigDecimal.ZERO,
			reorderLevel = BigDecimal("10"),
		)
		doAnswer { (it.arguments[0] as Material).also { m -> m.id = UUID.randomUUID() } }
			.whenever(materialRepository).save(any())

		materialService.create(request)

		verify(stockMovementRepository, never()).save(any())
	}

	@Test
	fun `a positive adjustment increases stock and records the delta`() {
		val material = material(stock = BigDecimal("50"))
		whenever(materialRepository.findByTenantIdAndId(tenantId, material.id!!)).thenReturn(material)
		doReturn(material).whenever(materialRepository).saveAndFlush(any())

		materialService.adjustStock(material.id!!, AdjustStockRequest(quantityDelta = BigDecimal("20"), note = "delivery"))

		assertEquals(0, BigDecimal("70").compareTo(material.stockQuantity))
		val captor = argumentCaptor<StockMovement>()
		verify(stockMovementRepository).save(captor.capture())
		assertEquals(StockMovementReason.MANUAL_ADJUSTMENT, captor.firstValue.reason)
		assertEquals("delivery", captor.firstValue.note)
	}

	@Test
	fun `an adjustment that would take stock below zero is rejected and nothing is saved`() {
		val material = material(stock = BigDecimal("5"))
		whenever(materialRepository.findByTenantIdAndId(tenantId, material.id!!)).thenReturn(material)

		assertThrows(InvalidStockAdjustmentException::class.java) {
			materialService.adjustStock(material.id!!, AdjustStockRequest(quantityDelta = BigDecimal("-10")))
		}

		assertEquals(0, BigDecimal("5").compareTo(material.stockQuantity))
		verify(materialRepository, never()).saveAndFlush(any())
		verify(stockMovementRepository, never()).save(any())
	}
}
